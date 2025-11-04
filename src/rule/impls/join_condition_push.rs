// Pushes single-table predicates from join condition down as filters on inputs
// Pattern: Join(Inner, LeftCond ∧ RightCond ∧ CrossCond, L, R) → Join(Inner, CrossCond, Filter(LeftCond, L), Filter(RightCond, R))
crate::rule! {
    JoinConditionPushRule {
        schemas: {
            left: (l: TL),
            right: (r: TR),
        },
        functions: {
            LeftCond(TL) -> Bool,
            RightCond(TR) -> Bool,
            CrossCond(TL, TR) -> Bool,
        },
        from: crate::join!(
            left,
            right,
            Inner,
            LeftCond(l) && RightCond(r) && CrossCond(l, r)
        ),
        to: {
            let filtered_left = crate::filter!(left, LeftCond(l));
            let filtered_right = crate::filter!(right, RightCond(r));
            crate::join!(filtered_left, filtered_right, Inner, CrossCond(l, r))
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{JoinType, LogicalPlanBuilder, col};
    use datafusion::prelude::lit;

    #[test]
    fn test_join_condition_push_all_three_types() {
        // SQL: SELECT * FROM emp JOIN dept
        //      ON emp.deptno = dept.deptno AND emp.salary > 1000 AND dept.deptno > 10
        // Pattern: Join(emp.deptno = dept.deptno AND emp.salary > 1000 AND dept.deptno > 10, emp, dept)
        // Result: Filter(emp.salary > 1000, emp) JOIN Filter(dept.deptno > 10, dept)
        //         ON emp.deptno = dept.deptno

        let emp = emp_table();
        let dept = dept_table();

        // Build join with all three predicate types
        let join_cond = col("emp.deptno")
            .eq(col("dept.deptno")) // Cross-table
            .and(col("emp.salary").gt(lit(1000.0))) // Left-only
            .and(col("dept.deptno").gt(lit(10))); // Right-only

        let input = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: Filters pushed to both sides, only cross-table in join
        let filtered_emp = LogicalPlanBuilder::from(emp.clone())
            .filter(col("emp.salary").gt(lit(1000.0)))
            .unwrap()
            .build()
            .unwrap();

        let filtered_dept = LogicalPlanBuilder::from(dept.clone())
            .filter(col("dept.deptno").gt(lit(10)))
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(filtered_emp)
            .join(
                filtered_dept,
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(col("emp.deptno").eq(col("dept.deptno"))),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinConditionPushRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_join_condition_push_multiple_left_predicates() {
        // Test multiple predicates on same side
        // SQL: SELECT * FROM sales JOIN product ON sales.product_id = product.product_id
        //      AND sales.sale_id > 100 AND sales.quantity > 5 AND product.price > 50
        // Pattern: Join(cross AND left1 AND left2 AND right, sales, product)
        // Result: Filter(left1 AND left2, sales) JOIN Filter(right, product) ON cross

        let sales = sales_table();
        let product = product_table();

        // Build join with multiple left-only predicates
        let join_cond = col("sales.product_id")
            .eq(col("product.product_id")) // Cross-table
            .and(col("sales.sale_id").gt(lit(100))) // Left-only 1
            .and(col("sales.quantity").gt(lit(5))) // Left-only 2
            .and(col("product.price").gt(lit(50.0))); // Right-only

        let input = LogicalPlanBuilder::from(sales.clone())
            .join(
                product.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: Multiple left predicates ANDed together in left filter
        let filtered_sales = LogicalPlanBuilder::from(sales.clone())
            .filter(
                col("sales.sale_id")
                    .gt(lit(100))
                    .and(col("sales.quantity").gt(lit(5))),
            )
            .unwrap()
            .build()
            .unwrap();

        let filtered_product = LogicalPlanBuilder::from(product.clone())
            .filter(col("product.price").gt(lit(50.0)))
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(filtered_sales)
            .join(
                filtered_product,
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(col("sales.product_id").eq(col("product.product_id"))),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinConditionPushRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_join_condition_push_only_cross_table() {
        // Edge case: only cross-table predicate exists (no single-table predicates to push)
        // SQL: SELECT * FROM emp JOIN dept ON emp.deptno = dept.deptno
        // Pattern: Join(LeftCond AND RightCond AND CrossCond, emp, dept)
        // Result: Should not match (LeftCond and RightCond remain unbound)

        let emp = emp_table();
        let dept = dept_table();

        let join_cond = col("emp.deptno").eq(col("dept.deptno"));

        let input = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinConditionPushRule;
        assert!(rule.try_apply(&input).is_err());
    }

    #[test]
    fn test_no_match_single_table() {
        // Should not match a single table scan
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp).build().unwrap();

        let rule = JoinConditionPushRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}

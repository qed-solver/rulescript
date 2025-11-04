// Pushes left-table predicates from join condition down as filter on left input
// Pattern: Join(Inner, LeftCond(l) ∧ JoinCond(l, r), L, R) → Join(Inner, JoinCond(l, r), Filter(LeftCond(l), L), R)
crate::rule! {
    JoinLeftConditionPushRule {
        schemas: {
            left: (l: TL),
            right: (r: TR),
        },
        functions: {
            LeftCond(TL) -> Bool,
            JoinCond(TL, TR) -> Bool,
        },
        from: crate::join!(
            left,
            right,
            Inner,
            LeftCond(l) && JoinCond(l, r)
        ),
        to: {
            let filtered_left = crate::filter!(left, LeftCond(l));
            crate::join!(filtered_left, right, Inner, JoinCond(l, r))
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
    fn test_join_left_condition_push() {
        // SQL: SELECT * FROM emp JOIN dept
        //      ON emp.deptno = dept.deptno AND emp.salary > 1000
        // Pattern: Join(emp.salary > 1000 AND emp.deptno = dept.deptno, emp, dept)
        // Result: Filter(emp.salary > 1000, emp) JOIN dept ON emp.deptno = dept.deptno

        let emp = emp_table();
        let dept = dept_table();

        // Build join with left-only predicate and cross-table predicate
        let join_cond = col("emp.salary")
            .gt(lit(1000.0))
            .and(col("emp.deptno").eq(col("dept.deptno")));

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

        // Expected: Filter pushed to left side
        let filtered_emp = LogicalPlanBuilder::from(emp.clone())
            .filter(col("emp.salary").gt(lit(1000.0)))
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(filtered_emp)
            .join(
                dept.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(col("emp.deptno").eq(col("dept.deptno"))),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinLeftConditionPushRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_join_left_condition_push_multiple() {
        // SQL: SELECT * FROM sales JOIN product 
        //      ON sales.product_id = product.product_id
        //      AND sales.sale_id > 100 AND sales.quantity > 5

        let sales = sales_table();
        let product = product_table();

        // Build join with multiple left-only predicates
        let join_cond = col("sales.sale_id")
            .gt(lit(100))
            .and(col("sales.quantity").gt(lit(5)))
            .and(col("sales.product_id").eq(col("product.product_id")));

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

        // Expected: Multiple left predicates pushed down together
        let filtered_sales = LogicalPlanBuilder::from(sales.clone())
            .filter(
                col("sales.sale_id")
                    .gt(lit(100))
                    .and(col("sales.quantity").gt(lit(5))),
            )
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(filtered_sales)
            .join(
                product.clone(),
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(col("sales.product_id").eq(col("product.product_id"))),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinLeftConditionPushRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_cross_condition_only() {
        // Should not match when only cross-table predicate exists
        let emp = emp_table();
        let dept = dept_table();

        let join_cond = col("emp.deptno").eq(col("dept.deptno"));

        let input = LogicalPlanBuilder::from(emp)
            .join(
                dept,
                JoinType::Inner,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_cond),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = JoinLeftConditionPushRule;
        assert!(rule.try_apply(&input).is_err());
    }

    #[test]
    fn test_no_match_single_table() {
        // Should not match a single table scan
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp).build().unwrap();

        let rule = JoinLeftConditionPushRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}
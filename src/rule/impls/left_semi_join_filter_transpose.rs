// Pushes a LeftSemi join down past a Filter by pulling the Filter up
// Pattern: LeftSemi(Filter(X), Y) → Filter(LeftSemi(X, Y))
// This exposes the LeftSemi join to other optimization rules
crate::rule! {
    LeftSemiJoinFilterTransposeRule {
        schemas: {
            left: (x: TL),
            right: (r: TR),
        },
        functions: {
            P(TL) -> Bool,
            C(TL, TR) -> Bool,
        },
        from: {
            let filtered = crate::filter!(left, P(x));
            crate::join!(filtered, right, LeftSemi, C(x, r))
        },
        to: {
            let semi_join = crate::join!(left, right, LeftSemi, C(x, r));
            crate::filter!(semi_join, P(x))
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{JoinType, LogicalPlanBuilder, col, lit};

    #[test]
    fn test_left_semi_join_filter_transpose_basic() {
        // Based on Calcite's testPushSemiJoinPastFilter
        // SQL: select e.ename from emp e, dept d
        //      where e.deptno = d.deptno and e.ename = 'foo'
        // Pattern: LeftSemi(Filter(emp, ename='foo'), dept)
        // Expected: Filter(LeftSemi(emp, dept), ename='foo')

        let emp = emp_table();
        let dept = dept_table();

        // Build: Filter(emp, ename = 'foo')
        let filtered_emp = LogicalPlanBuilder::from(emp.clone())
            .filter(col("ename").eq(lit("foo")))
            .unwrap()
            .build()
            .unwrap();

        // Build: LeftSemi Join(Filter(emp), dept) on emp.deptno = dept.deptno
        let join_filter = col("emp.deptno").eq(col("dept.deptno"));
        let input = LogicalPlanBuilder::from(filtered_emp)
            .join(
                dept.clone(),
                JoinType::LeftSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: LeftSemi Join(emp, dept) then Filter(ename = 'foo')
        let semi_join = LogicalPlanBuilder::from(emp)
            .join(
                dept,
                JoinType::LeftSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter),
            )
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(semi_join)
            .filter(col("ename").eq(lit("foo")))
            .unwrap()
            .build()
            .unwrap();

        let rule = LeftSemiJoinFilterTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_left_semi_join_filter_transpose_complex_filter() {
        // Test with more complex filter predicate
        // Pattern: LeftSemi(Filter(emp, ename='foo' AND salary > 50000), dept)
        // Expected: Filter(LeftSemi(emp, dept), ename='foo' AND salary > 50000)

        let emp = emp_table();
        let dept = dept_table();

        // Build: Filter(emp, ename = 'foo' AND salary > 50000)
        let filter_pred = col("ename")
            .eq(lit("foo"))
            .and(col("salary").gt(lit(50000.0)));
        let filtered_emp = LogicalPlanBuilder::from(emp.clone())
            .filter(filter_pred.clone())
            .unwrap()
            .build()
            .unwrap();

        // Build: LeftSemi Join(Filter(emp), dept)
        let join_filter = col("emp.deptno").eq(col("dept.deptno"));
        let input = LogicalPlanBuilder::from(filtered_emp)
            .join(
                dept.clone(),
                JoinType::LeftSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: Filter(SemiJoin(emp, dept), ename='foo' AND salary > 50000)
        let semi_join = LogicalPlanBuilder::from(emp)
            .join(
                dept,
                JoinType::LeftSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter),
            )
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(semi_join)
            .filter(filter_pred)
            .unwrap()
            .build()
            .unwrap();

        let rule = LeftSemiJoinFilterTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_left_semi_join_filter_transpose_multiple_tables() {
        // Test with different table configurations
        // Pattern: LeftSemi(Filter(sales, quantity > 10), product)
        // Expected: Filter(LeftSemi(sales, product), quantity > 10)

        let sales = sales_table();
        let product = product_table();

        // Build: Filter(sales, quantity > 10)
        let filtered_sales = LogicalPlanBuilder::from(sales.clone())
            .filter(col("quantity").gt(lit(10)))
            .unwrap()
            .build()
            .unwrap();

        // Build: LeftSemi Join(Filter(sales), product)
        let join_filter = col("sales.product_id").eq(col("product.product_id"));
        let input = LogicalPlanBuilder::from(filtered_sales)
            .join(
                product.clone(),
                JoinType::LeftSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: Filter(SemiJoin(sales, product), quantity > 10)
        let semi_join = LogicalPlanBuilder::from(sales)
            .join(
                product,
                JoinType::LeftSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter),
            )
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(semi_join)
            .filter(col("quantity").gt(lit(10)))
            .unwrap()
            .build()
            .unwrap();

        let rule = LeftSemiJoinFilterTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_inner_join() {
        // Should not match Inner join with filter (only LeftSemi)
        let emp = emp_table();
        let dept = dept_table();

        let filtered_emp = LogicalPlanBuilder::from(emp.clone())
            .filter(col("ename").eq(lit("foo")))
            .unwrap()
            .build()
            .unwrap();

        let join_filter = col("emp.deptno").eq(col("dept.deptno"));
        let plan = LogicalPlanBuilder::from(filtered_emp)
            .join(
                dept,
                JoinType::Inner, // Not LeftSemi
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = LeftSemiJoinFilterTransposeRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_no_match_left_semi_join_without_filter() {
        // Should not match LeftSemi join without filter on left side
        let emp = emp_table();
        let dept = dept_table();

        let join_filter = col("emp.deptno").eq(col("dept.deptno"));
        let plan = LogicalPlanBuilder::from(emp)
            .join(
                dept,
                JoinType::LeftSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = LeftSemiJoinFilterTransposeRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}

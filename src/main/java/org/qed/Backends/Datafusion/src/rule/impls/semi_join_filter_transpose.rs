// Transposes filter past semi joins to expose the semi join to other optimization rules

// Pulls filter up past a LeftSemi join
// Pattern: LeftSemi(Filter(P(x), X), Y, C(x, r)) → Filter(P(x), LeftSemi(X, Y, C(x, r)))
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

// Pulls filter up past a RightSemi join
// Pattern: RightSemi(X, Filter(P(r), Y), C(x, r)) → Filter(P(r), RightSemi(X, Y, C(x, r)))
crate::rule! {
    RightSemiJoinFilterTransposeRule {
        schemas: {
            left: (x: TL),
            right: (r: TR),
        },
        functions: {
            P(TR) -> Bool,
            C(TL, TR) -> Bool,
        },
        from: {
            let filtered = crate::filter!(right, P(r));
            crate::join!(left, filtered, RightSemi, C(x, r))
        },
        to: {
            let semi_join = crate::join!(left, right, RightSemi, C(x, r));
            crate::filter!(semi_join, P(r))
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{JoinType, LogicalPlanBuilder, col, lit};

    // ==================== LeftSemiJoinFilterTransposeRule ====================

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
    fn test_left_no_match_inner_join() {
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
    fn test_left_no_match_without_filter() {
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

    // ==================== RightSemiJoinFilterTransposeRule ====================

    #[test]
    fn test_right_semi_join_filter_transpose_basic() {
        // Mirror of left semi join test
        // Pattern: RightSemi(emp, Filter(dept, dname='Sales'))
        // Expected: Filter(RightSemi(emp, dept), dname='Sales')

        let emp = emp_table();
        let dept = dept_table();

        // Build: Filter(dept, dname = 'Sales')
        let filtered_dept = LogicalPlanBuilder::from(dept.clone())
            .filter(col("dname").eq(lit("Sales")))
            .unwrap()
            .build()
            .unwrap();

        // Build: RightSemi Join(emp, Filter(dept)) on emp.deptno = dept.deptno
        let join_filter = col("emp.deptno").eq(col("dept.deptno"));
        let input = LogicalPlanBuilder::from(emp.clone())
            .join(
                filtered_dept,
                JoinType::RightSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: RightSemi Join(emp, dept) then Filter(dname = 'Sales')
        let semi_join = LogicalPlanBuilder::from(emp)
            .join(
                dept,
                JoinType::RightSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter),
            )
            .unwrap()
            .build()
            .unwrap();

        let expected = LogicalPlanBuilder::from(semi_join)
            .filter(col("dname").eq(lit("Sales")))
            .unwrap()
            .build()
            .unwrap();

        let rule = RightSemiJoinFilterTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_right_semi_join_filter_transpose_complex_filter() {
        // Test with more complex filter predicate
        // Pattern: RightSemi(sales, Filter(product, price > 100 AND name = 'Widget'))
        // Expected: Filter(RightSemi(sales, product), price > 100 AND name = 'Widget')

        let sales = sales_table();
        let product = product_table();

        // Build: Filter(product, price > 100 AND name = 'Widget')
        let filter_pred = col("price")
            .gt(lit(100.0))
            .and(col("name").eq(lit("Widget")));
        let filtered_product = LogicalPlanBuilder::from(product.clone())
            .filter(filter_pred.clone())
            .unwrap()
            .build()
            .unwrap();

        // Build: RightSemi Join(sales, Filter(product))
        let join_filter = col("sales.product_id").eq(col("product.product_id"));
        let input = LogicalPlanBuilder::from(sales.clone())
            .join(
                filtered_product,
                JoinType::RightSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter.clone()),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: Filter(RightSemi(sales, product), price > 100 AND name = 'Widget')
        let semi_join = LogicalPlanBuilder::from(sales)
            .join(
                product,
                JoinType::RightSemi,
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

        let rule = RightSemiJoinFilterTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_right_no_match_inner_join() {
        // Should not match Inner join with filter on right (only RightSemi)
        let emp = emp_table();
        let dept = dept_table();

        let filtered_dept = LogicalPlanBuilder::from(dept.clone())
            .filter(col("dname").eq(lit("Sales")))
            .unwrap()
            .build()
            .unwrap();

        let join_filter = col("emp.deptno").eq(col("dept.deptno"));
        let plan = LogicalPlanBuilder::from(emp)
            .join(
                filtered_dept,
                JoinType::Inner, // Not RightSemi
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = RightSemiJoinFilterTransposeRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_right_no_match_without_filter() {
        // Should not match RightSemi join without filter on right side
        let emp = emp_table();
        let dept = dept_table();

        let join_filter = col("emp.deptno").eq(col("dept.deptno"));
        let plan = LogicalPlanBuilder::from(emp)
            .join(
                dept,
                JoinType::RightSemi,
                (Vec::<String>::new(), Vec::<String>::new()),
                Some(join_filter),
            )
            .unwrap()
            .build()
            .unwrap();

        let rule = RightSemiJoinFilterTransposeRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}

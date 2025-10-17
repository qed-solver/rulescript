// Pushes a Filter below a Projection by rewriting the filter predicate
// Pattern: Filter(P(y), Project(f(x), source)) → Project(f(x), Filter(P(f(x)), source))
// where y are projection output columns and x are source columns
crate::rule! {
    FilterProjectTransposeRule {
        schemas: {
            source: (x: T),
        },
        functions: {
            f(T) -> Tf,
            P(Tf) -> Bool,
        },
        from: {
            let inner = crate::project!(source, [f(x) as f_output]);
            crate::filter!(inner, P(f_output))
        },
        to: {
            let filtered = crate::filter!(source, P(f(x)));
            crate::project!(filtered, [f(x)])
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{LogicalPlanBuilder, col, lit};

    #[test]
    fn test_filter_project_transpose_basic() {
        // Based on Calcite's FilterProjectTransposeRule tests
        // Concrete plan: source → project(salary, dept_id) → filter(salary > 50000)
        // Expected: source → filter(salary > 50000) → project(salary, dept_id)

        let emp = emp_table();

        // Build: emp.project(salary, deptno).filter(salary > 50000)
        let input = LogicalPlanBuilder::from(emp.clone())
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .filter(col("salary").gt(lit(50000.0)))
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.filter(salary > 50000).project(salary, deptno)
        let expected = LogicalPlanBuilder::from(emp)
            .filter(col("salary").gt(lit(50000.0)))
            .unwrap()
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_project_transpose_multiple_columns() {
        // Test with filter referencing multiple projected columns
        let emp = emp_table();

        // Build: emp.project(salary, deptno).filter(salary > 50000 AND deptno = 10)
        let input = LogicalPlanBuilder::from(emp.clone())
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .filter(
                col("salary")
                    .gt(lit(50000.0))
                    .and(col("deptno").eq(lit(10))),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.filter(salary > 50000 AND deptno = 10).project(salary, deptno)
        let expected = LogicalPlanBuilder::from(emp)
            .filter(
                col("salary")
                    .gt(lit(50000.0))
                    .and(col("deptno").eq(lit(10))),
            )
            .unwrap()
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_project_transpose_with_expression() {
        // Based on Calcite's testFilterProjectTranspose
        // Test with projection containing an expression, not just column references
        // SQL equivalent: SELECT deptno * 2 AS twiceDeptno FROM dept
        //                 WHERE twiceDeptno = 20
        let dept = dept_table();

        // Build: dept.project(deptno * 2 as doubled).filter(doubled = 20)
        let input = LogicalPlanBuilder::from(dept.clone())
            .project(vec![(col("deptno") * lit(2)).alias("doubled")])
            .unwrap()
            .filter(col("doubled").eq(lit(20)))
            .unwrap()
            .build()
            .unwrap();

        // Expected: dept.filter(deptno * 2 = 20).project(deptno * 2 as doubled)
        // The filter predicate is "inlined" with the projection expression
        let expected = LogicalPlanBuilder::from(dept)
            .filter((col("deptno") * lit(2)).eq(lit(20)))
            .unwrap()
            .project(vec![(col("deptno") * lit(2)).alias("doubled")])
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_project_transpose_subset_columns() {
        // Based on Calcite's testFilterProjectTransposeRule
        // Project a single column, then filter on it
        // This tests that the rule works when projection reduces columns
        let emp = emp_table();

        // Build: emp.project(salary).filter(salary = 11500)
        let input = LogicalPlanBuilder::from(emp.clone())
            .project(vec![col("salary")])
            .unwrap()
            .filter(col("salary").eq(lit(11500.0)))
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.filter(salary = 11500).project(salary)
        let expected = LogicalPlanBuilder::from(emp)
            .filter(col("salary").eq(lit(11500.0)))
            .unwrap()
            .project(vec![col("salary")])
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_filter_project_transpose_complex_predicate() {
        // Test with more complex filter predicate (OR condition)
        let emp = emp_table();

        // Build: emp.project(salary, deptno).filter((salary > 50000) OR (deptno = 10))
        let input = LogicalPlanBuilder::from(emp.clone())
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .filter(col("salary").gt(lit(50000.0)).or(col("deptno").eq(lit(10))))
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.filter((salary > 50000) OR (deptno = 10)).project(salary, deptno)
        let expected = LogicalPlanBuilder::from(emp)
            .filter(col("salary").gt(lit(50000.0)).or(col("deptno").eq(lit(10))))
            .unwrap()
            .project(vec![col("salary"), col("deptno")])
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_filter_only() {
        // Should not match a filter without a projection below it
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp)
            .filter(col("salary").gt(lit(50000.0)))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterProjectTransposeRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}

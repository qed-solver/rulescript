/// Merges two consecutive projections via function composition
/// Pattern: source.project(f).project(g) → source.project(g∘f)
crate::rule! {
    ProjectMergeRule {
        schemas: {
            source: (col: Tc),
        },
        functions: {
            f(Tc) -> Tf,
            g(Tf) -> Tg,
        },
        from: {
            let inner = crate::project!(source, [f(col) as f_output]);
            crate::project!(inner, [g(f_output)])
        },
        to: crate::project!(source, [g(f(col))]),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{test::utils::*, ApplicableRule};
    use datafusion::logical_expr::{LogicalPlanBuilder, col, lit};

    #[test]
    fn test_project_merge_calcite_style() {
        // Based on typical Calcite patterns: nested projections with expressions
        // SQL equivalent: SELECT x * 2 FROM (SELECT salary + 1000 as x FROM emp)

        let emp = emp_table();

        // Build: emp.project(salary + 1000 as x).project(x * 2)
        let input = LogicalPlanBuilder::from(emp.clone())
            .project(vec![(col("salary") + lit(1000.0)).alias("x")])
            .unwrap()
            .project(vec![(col("x") * lit(2.0)).alias("result")])
            .unwrap()
            .build()
            .unwrap();

        // Expected: emp.project((salary + 1000 as x) * 2)
        // The rule should merge the two projections into one
        let expected = LogicalPlanBuilder::from(emp)
            .project(vec![
                ((col("salary") + lit(1000.0)).alias("x") * lit(2.0)).alias("result"),
            ])
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectMergeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_project_merge_with_column_rename() {
        // Test case: consecutive projections that rename columns
        // SQL equivalent: SELECT ename FROM (SELECT name as ename FROM dept)

        let dept = dept_table();

        // Build: dept.project(dname as name).project(name as final_name)
        let input = LogicalPlanBuilder::from(dept.clone())
            .project(vec![col("dname").alias("name")])
            .unwrap()
            .project(vec![col("name").alias("final_name")])
            .unwrap()
            .build()
            .unwrap();

        // Expected: dept.project(dname as name as final_name)
        let expected = LogicalPlanBuilder::from(dept)
            .project(vec![col("dname").alias("name").alias("final_name")])
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectMergeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_single_projection() {
        // Should not match a single projection
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp)
            .project(vec![col("empno"), col("ename")])
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectMergeRule;
        assert!(
            rule.try_apply(&plan).is_err(),
            "Should not match single projection"
        );
    }
}

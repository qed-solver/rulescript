// Removes identity projections (when projection outputs all columns in same order)
// Pattern: source.project([col]) → source
// Where [col] represents ALL columns in order
crate::rule! {
    ProjectRemoveRule {
        schemas: {
            source: (col: T),
        },
        functions: {},
        from: crate::project!(source, [col]),
        to: source,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::{ApplicableRule, test::utils::*};
    use datafusion::logical_expr::{LogicalPlanBuilder, col};

    #[test]
    fn test_project_remove_identity_dept() {
        // Test with Calcite's dept table
        // Identity projection: all columns in same order
        let dept = dept_table();

        let input = LogicalPlanBuilder::from(dept.clone())
            .project(vec![col("deptno"), col("dname")])
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectRemoveRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, dept);
    }

    #[test]
    fn test_project_remove_identity_emp() {
        // Test with Calcite's emp table
        // Identity projection: all columns in same order
        let emp = emp_table();

        let input = LogicalPlanBuilder::from(emp.clone())
            .project(vec![
                col("empno"),
                col("ename"),
                col("deptno"),
                col("salary"),
            ])
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectRemoveRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, emp);
    }

    #[test]
    fn test_no_match_reordered() {
        // Reordered columns - should not match
        let dept = dept_table();

        let plan = LogicalPlanBuilder::from(dept)
            .project(vec![col("dname"), col("deptno")]) // Reversed order
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectRemoveRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_no_match_subset() {
        // Subset of columns - should not match
        let emp = emp_table();

        let plan = LogicalPlanBuilder::from(emp)
            .project(vec![col("empno"), col("ename")]) // Only first two columns
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectRemoveRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_no_match_with_expressions() {
        // Projection with expressions, not just columns
        let emp = emp_table();
        use datafusion::logical_expr::lit;

        let plan = LogicalPlanBuilder::from(emp)
            .project(vec![
                col("empno"),
                col("ename"),
                col("deptno"),
                (col("salary") * lit(1.1)).alias("adjusted_salary"), // Expression, not identity
            ])
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectRemoveRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_project_remove_identity_over_join() {
        // Test with join as source (also serves as ProjectJoinRemoveRule)
        // Identity projection over join: all columns in same order
        use datafusion::logical_expr::JoinType;
        
        let emp = emp_table();
        let dept = dept_table();

        let joined = LogicalPlanBuilder::from(emp.clone())
            .join(
                dept.clone(),
                JoinType::Inner,
                (vec!["deptno"], vec!["deptno"]),
                None,
            )
            .unwrap()
            .build()
            .unwrap();

        let join_schema = joined.schema();
        let all_cols: Vec<_> = join_schema.columns().into_iter().map(col).collect();
        
        let input = LogicalPlanBuilder::from(joined.clone())
            .project(all_cols)
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectRemoveRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, joined);
    }
}

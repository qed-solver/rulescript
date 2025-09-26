use crate::{
    ast::{
        opaque::{Field, Schema, Type},
        relational::Rel,
    },
    matcher::DefaultMatcher,
    rule::{ApplicableRule, RewriteRule},
};
use datafusion::logical_expr::col;

/// Removes identity projections (when projection outputs all columns in same order)
/// Pattern: source.project([col]) → source
/// Where [col] represents ALL columns in order
pub struct ProjectRemoveRule;

impl RewriteRule for ProjectRemoveRule {
    fn from(&self) -> Rel {
        // Create a generic schema
        let schema = Schema {
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic {
                    id: "T".to_string(),
                },
                nullable: true,
            }],
        };

        // Pattern: source.project([col])
        let source = Rel::source("source".to_string(), schema);

        // Create a projection with just column references
        // During matching, this single column pattern will match against
        // the full list of columns if they're in the same order
        source.project(vec![col("col")]).unwrap()
    }

    fn to(&self) -> Rel {
        // Same schema as pattern
        let schema = Schema {
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic {
                    id: "T".to_string(),
                },
                nullable: true,
            }],
        };

        // Replacement: just the source (remove projection)
        Rel::source("source".to_string(), schema)
    }

    fn name(&self) -> &str {
        "ProjectRemoveRule"
    }
}

impl ApplicableRule<DefaultMatcher> for ProjectRemoveRule {}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::test::utils::*;
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
}

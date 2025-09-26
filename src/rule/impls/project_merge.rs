use crate::{
    ast::{
        opaque::{Field, Schema, Type},
        relational::Rel,
        scalar::Function,
    },
    matcher::DefaultMatcher,
    rule::{ApplicableRule, RewriteRule},
};
use datafusion::logical_expr::col;

/// Merges two consecutive projections via function composition
/// Pattern: source.project(f).project(g) → source.project(g∘f)
pub struct ProjectMergeRule;

impl RewriteRule for ProjectMergeRule {
    fn from(&self) -> Rel {
        // Create a simple single-column schema
        let schema = Schema {
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic {
                    id: "Tc".to_string(),
                },
                nullable: true,
            }],
        };

        // Create abstract projection functions with explicit types
        let f = Function::new(
            "f".to_string(),
            vec![Type::Generic {
                id: "Tc".to_string(),
            }],
            Type::Generic {
                id: "Tf".to_string(),
            },
        );
        let g = Function::new(
            "g".to_string(),
            vec![Type::Generic {
                id: "Tf".to_string(),
            }],
            Type::Generic {
                id: "Tg".to_string(),
            },
        );

        // Pattern: source.project(f(a)).project(g(f_output))
        // The second projection references the output of the first
        let source = Rel::source("source".to_string(), schema);

        // First projection creates new columns with an alias
        let first_proj = source
            .project(vec![f.call(vec![col("col")]).alias("f_output")])
            .unwrap();

        // Second projection operates on the output of the first
        // Now it can reference the aliased column
        first_proj
            .project(vec![g.call(vec![col("f_output")])])
            .unwrap()
    }

    fn to(&self) -> Rel {
        // Same single-column schema
        let schema = Schema {
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic {
                    id: "Tc".to_string(),
                },
                nullable: true,
            }],
        };

        // Create abstract functions with explicit types matching the pattern
        let f = Function::new(
            "f".to_string(),
            vec![Type::Generic {
                id: "Tc".to_string(),
            }],
            Type::Generic {
                id: "Tf".to_string(),
            },
        );
        let g = Function::new(
            "g".to_string(),
            vec![Type::Generic {
                id: "Tf".to_string(),
            }],
            Type::Generic {
                id: "Tg".to_string(),
            },
        );

        // Replacement: source.project(g(f(a)))
        // This represents the composition g∘f
        let source = Rel::source("source".to_string(), schema);
        source
            .project(vec![g.call(vec![f.call(vec![col("col")])])])
            .unwrap()
    }

    fn name(&self) -> &str {
        "ProjectMergeRule"
    }
}

impl ApplicableRule<DefaultMatcher> for ProjectMergeRule {}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::test::utils::*;
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

        // Expected: emp.project((salary + 1000) * 2)
        // The rule should merge the two projections into one
        let expected = LogicalPlanBuilder::from(emp)
            .project(vec![
                ((col("salary") + lit(1000.0)) * lit(2.0)).alias("result"),
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

        // Expected: dept.project(dname as final_name)
        let expected = LogicalPlanBuilder::from(dept)
            .project(vec![col("dname").alias("final_name")])
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

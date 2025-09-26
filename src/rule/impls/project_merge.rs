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
        // Create a generic schema
        let schema = Schema {
            fields: vec![
                Field {
                    name: "a".to_string(),
                    data_type: Type::Generic {
                        id: "T1".to_string(),
                    },
                    nullable: true,
                },
                Field {
                    name: "b".to_string(),
                    data_type: Type::Generic {
                        id: "T2".to_string(),
                    },
                    nullable: true,
                },
            ],
        };

        // Create abstract projection functions with explicit types
        let f = Function::new(
            "f".to_string(),
            vec![
                Type::Generic { id: "T1".to_string() },
                Type::Generic { id: "T2".to_string() },
            ],
            Type::Generic { id: "Tf".to_string() },
        );
        let g = Function::new(
            "g".to_string(),
            vec![Type::Generic { id: "Tf".to_string() }],
            Type::Generic { id: "Tg".to_string() },
        );

        // Pattern: source.project(f(a, b)).project(g(f_output))
        // The second projection references the output of the first
        let source = Rel::source("source".to_string(), schema);
        
        // First projection creates new columns
        let first_proj = source
            .project(vec![f.call(vec![col("a"), col("b")])])
            .unwrap();
        
        // Second projection operates on the output of the first
        // Note: g now expects 1 argument based on our type definition
        first_proj
            .project(vec![g.call(vec![col("f")])])
            .unwrap()
    }

    fn to(&self) -> Rel {
        // Same schema
        let schema = Schema {
            fields: vec![
                Field {
                    name: "a".to_string(),
                    data_type: Type::Generic {
                        id: "T1".to_string(),
                    },
                    nullable: true,
                },
                Field {
                    name: "b".to_string(),
                    data_type: Type::Generic {
                        id: "T2".to_string(),
                    },
                    nullable: true,
                },
            ],
        };

        // Create abstract functions with explicit types matching the pattern
        let f = Function::new(
            "f".to_string(),
            vec![
                Type::Generic { id: "T1".to_string() },
                Type::Generic { id: "T2".to_string() },
            ],
            Type::Generic { id: "Tf".to_string() },
        );
        let g = Function::new(
            "g".to_string(),
            vec![Type::Generic { id: "Tf".to_string() }],
            Type::Generic { id: "Tg".to_string() },
        );

        // Replacement: source.project(g(f(a, b)))
        // This represents the composition g∘f
        let source = Rel::source("source".to_string(), schema);
        source
            .project(vec![g.call(vec![f.call(vec![col("a"), col("b")])])])
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
    use datafusion::logical_expr::{LogicalPlan, LogicalPlanBuilder};

    #[tokio::test]
    async fn test_project_merge_basic() {
        // Create test input: source.project(age + 1).project(col * 2)
        let source = test_table_scan("employees").await;
        
        // Build input plan with two projections
        let input_plan = LogicalPlanBuilder::from(source.clone())
            .project(vec![
                (col("age") + lit(1)).alias("new_age"),
                col("salary"),
            ])
            .unwrap()
            .project(vec![
                (col("new_age") * lit(2)).alias("final_age"),
                col("salary"),
            ])
            .unwrap()
            .build()
            .unwrap();

        // Build expected output: single projection with composed expressions
        // (age + 1) * 2 = final_age
        let expected_plan = LogicalPlanBuilder::from(source)
            .project(vec![
                ((col("age") + lit(1)) * lit(2)).alias("final_age"),
                col("salary"),
            ])
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = ProjectMergeRule;
        let result = rule.try_apply(&input_plan);

        assert!(result.is_ok(), "Rule should apply successfully");
        let actual_plan = result.unwrap();
        
        // Compare the actual result with expected
        assert_eq!(
            actual_plan, expected_plan,
            "Transformed plan does not match expected.\nActual:\n{:?}\n\nExpected:\n{:?}",
            actual_plan, expected_plan
        );
    }

    #[tokio::test]
    async fn test_project_merge_complex_composition() {
        // Create test with more complex expressions
        let source = test_table_scan("employees").await;
        
        let input_plan = LogicalPlanBuilder::from(source.clone())
            .project(vec![
                col("id"),
                (col("age") + col("salary") / lit(1000.0)).alias("score"),
            ])
            .unwrap()
            .project(vec![
                col("id"),
                (col("score") * lit(100.0)).alias("final_score"),
            ])
            .unwrap()
            .build()
            .unwrap();

        // Expected: single projection with composed expression
        // (age + salary/1000) * 100 = final_score
        let expected_plan = LogicalPlanBuilder::from(source)
            .project(vec![
                col("id"),
                ((col("age") + col("salary") / lit(1000.0)) * lit(100.0)).alias("final_score"),
            ])
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = ProjectMergeRule;
        let result = rule.try_apply(&input_plan);

        assert!(result.is_ok(), "Should merge complex projections");
        let actual_plan = result.unwrap();
        
        assert_eq!(
            actual_plan, expected_plan,
            "Transformed plan does not match expected.\nActual:\n{:?}\n\nExpected:\n{:?}",
            actual_plan, expected_plan
        );
    }

    #[tokio::test]
    async fn test_project_merge_no_match_single() {
        // Test with single projection - should not match
        let source = test_table_scan("employees").await;
        
        let plan = LogicalPlanBuilder::from(source.clone())
            .project(vec![col("id"), col("name")])
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = ProjectMergeRule;
        let result = rule.try_apply(&plan);

        assert!(result.is_err(), "Should not match single projection");
    }

    use datafusion::logical_expr::lit;
}
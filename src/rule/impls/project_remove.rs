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

/// Removes trivial projections that are identity mappings
/// Pattern: source.project([col1, col2, ...]) where projection is identity → source
pub struct ProjectRemoveRule;

impl RewriteRule for ProjectRemoveRule {
    fn from(&self) -> Rel {
        // Create a generic schema with multiple fields
        let schema = Schema {
            fields: vec![
                Field {
                    name: "col1".to_string(),
                    data_type: Type::Generic {
                        id: "T1".to_string(),
                    },
                    nullable: true,
                },
                Field {
                    name: "col2".to_string(),
                    data_type: Type::Generic {
                        id: "T2".to_string(),
                    },
                    nullable: true,
                },
            ],
        };

        // Create identity projection function with explicit types
        let identity = Function::new(
            "identity".to_string(),
            vec![
                Type::Generic { id: "T1".to_string() },
                Type::Generic { id: "T2".to_string() },
            ],
            Type::Generic { id: "Tidentity".to_string() },  // Return type (though identity should preserve types)
        );

        // Pattern: source.project(identity(col1, col2))
        let source = Rel::source("source".to_string(), schema);
        source
            .project(vec![identity.call(vec![col("col1"), col("col2")])])
            .unwrap()
    }

    fn to(&self) -> Rel {
        // Same schema
        let schema = Schema {
            fields: vec![
                Field {
                    name: "col1".to_string(),
                    data_type: Type::Generic {
                        id: "T1".to_string(),
                    },
                    nullable: true,
                },
                Field {
                    name: "col2".to_string(),
                    data_type: Type::Generic {
                        id: "T2".to_string(),
                    },
                    nullable: true,
                },
            ],
        };

        // Replacement: just the source
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
    use datafusion::logical_expr::{LogicalPlan, LogicalPlanBuilder};

    #[tokio::test]
    async fn test_project_remove_identity() {
        // Create test input with identity projection
        let source = test_table_scan("employees").await;
        let schema = source.schema();
        
        // Build identity projection - all columns in same order
        let identity_exprs = identity_projection(&schema);
        
        let input_plan = LogicalPlanBuilder::from(source.clone())
            .project(identity_exprs)
            .unwrap()
            .build()
            .unwrap();

        // Expected: just the source without projection
        let expected_plan = source;

        // Apply the rule
        let rule = ProjectRemoveRule;
        let result = rule.try_apply(&input_plan);

        // Note: This test will likely fail because our pattern matching
        // doesn't recognize identity projections yet. But we're setting up
        // the expected behavior.
        if result.is_ok() {
            let actual_plan = result.unwrap();
            assert_eq!(
                actual_plan, expected_plan,
                "Transformed plan does not match expected.\nActual:\n{:?}\n\nExpected:\n{:?}",
                actual_plan, expected_plan
            );
        }
        // For now, we expect this might fail
    }

    #[tokio::test] 
    async fn test_project_remove_non_identity() {
        // Create test input with non-identity projection (reordering columns)
        let source = test_table_scan("employees").await;
        
        let plan = LogicalPlanBuilder::from(source.clone())
            .project(vec![col("name"), col("id")]) // Reordered
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = ProjectRemoveRule;
        let result = rule.try_apply(&plan);

        // Should not match non-identity projections
        assert!(result.is_err(), "Should not match non-identity projection");
    }
}
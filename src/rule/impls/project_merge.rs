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
                name: "a".to_string(),
                data_type: Type::Generic {
                    id: "T1".to_string(),
                },
                nullable: true,
            }],
        };

        // Create abstract projection functions with explicit types
        let f = Function::new(
            "f".to_string(),
            vec![Type::Generic {
                id: "T1".to_string(),
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
            .project(vec![f.call(vec![col("a")]).alias("f_output")])
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
                name: "a".to_string(),
                data_type: Type::Generic {
                    id: "T1".to_string(),
                },
                nullable: true,
            }],
        };

        // Create abstract functions with explicit types matching the pattern
        let f = Function::new(
            "f".to_string(),
            vec![Type::Generic {
                id: "T1".to_string(),
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
            .project(vec![g.call(vec![f.call(vec![col("a")])])])
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
    use datafusion::{
        arrow::datatypes::{DataType, Field, Schema as ArrowSchema},
        logical_expr::{col, LogicalPlan, LogicalPlanBuilder},
    };
    use std::sync::Arc;

    fn test_source() -> LogicalPlan {
        // Create a simple test schema
        let schema = Arc::new(ArrowSchema::new(vec![
            Field::new("id", DataType::Int32, false),
            Field::new("age", DataType::Int32, true),
            Field::new("salary", DataType::Float64, true),
        ]));

        let table_source = Arc::new(datafusion::logical_expr::builder::LogicalTableSource::new(
            schema,
        ));

        LogicalPlanBuilder::scan("test", table_source, None)
            .unwrap()
            .build()
            .unwrap()
    }

    #[test]
    fn test_project_merge_basic() {
        // For now, just test that the pattern compiles correctly
        // The actual matching would require plans with abstract functions
        let rule = ProjectMergeRule;
        let pattern = rule.from();
        let replacement = rule.to();

        // Verify the patterns are valid plans
        assert!(matches!(pattern.plan, LogicalPlan::Projection(_)));
        assert!(matches!(replacement.plan, LogicalPlan::Projection(_)));
    }

    #[test]
    fn test_project_merge_no_match_single() {
        // Test with single projection - should not match
        let source = test_source();

        let plan = LogicalPlanBuilder::from(source)
            .project(vec![col("id"), col("age")])
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = ProjectMergeRule;
        let result = rule.try_apply(&plan);

        assert!(result.is_err(), "Should not match single projection");
    }
}

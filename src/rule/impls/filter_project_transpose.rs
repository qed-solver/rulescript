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

/// Pushes filter below projection when possible
/// Pattern: Filter(P(f(x)), Project(f(x), source)) → Project(f(x), Filter(P(x), source))
pub struct FilterProjectTransposeRule;

impl RewriteRule for FilterProjectTransposeRule {
    fn from(&self) -> Rel {
        let schema = Schema {
            fields: vec![Field {
                name: "x".to_string(),
                data_type: Type::Generic {
                    id: "T".to_string(),
                },
                nullable: true,
            }],
        };

        let f = Function::new(
            "f".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Generic {
                id: "Tf".to_string(),
            },
        );

        let p = Function::new(
            "P".to_string(),
            vec![Type::Generic {
                id: "Tf".to_string(),
            }],
            Type::Boolean,
        );

        let source = Rel::source("source".to_string(), schema);

        // Pattern: source.project(f(x)).filter(P(f_output))
        let proj = source
            .project(vec![f.call(vec![col("x")]).alias("f_output")])
            .unwrap();

        proj.filter(p.call(vec![col("f_output")])).unwrap()
    }

    fn to(&self) -> Rel {
        let schema = Schema {
            fields: vec![Field {
                name: "x".to_string(),
                data_type: Type::Generic {
                    id: "T".to_string(),
                },
                nullable: true,
            }],
        };

        let f = Function::new(
            "f".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Generic {
                id: "Tf".to_string(),
            },
        );

        let p = Function::new(
            "P".to_string(),
            vec![Type::Generic {
                id: "Tf".to_string(),
            }],
            Type::Boolean,
        );

        let source = Rel::source("source".to_string(), schema);

        // Replacement: source.filter(P(f(x))).project(f(x))
        source
            .filter(p.call(vec![f.call(vec![col("x")])]))
            .unwrap()
            .project(vec![f.call(vec![col("x")]).alias("f_output")])
            .unwrap()
    }

    fn name(&self) -> &str {
        "FilterProjectTransposeRule"
    }
}

impl ApplicableRule<DefaultMatcher> for FilterProjectTransposeRule {}

#[cfg(test)]
mod tests {
    use super::*;
    use datafusion::logical_expr::LogicalPlan;

    #[test]
    fn test_filter_project_transpose_creates_valid_plans() {
        use crate::ast::opaque::Type;
        use crate::rule::test::utils::*;
        use datafusion::logical_expr::{LogicalPlanBuilder, col};

        // This test verifies we can create the structure the rule expects
        let source = table_with_binary_columns(vec!["x"]);

        let f = test_function(
            "f",
            Type::Generic {
                id: "T".to_string(),
            },
            Type::Generic {
                id: "Tf".to_string(),
            },
        );
        let p = test_predicate(
            "P",
            Type::Generic {
                id: "Tf".to_string(),
            },
        );

        // Create filter-on-projection plan
        let proj = LogicalPlanBuilder::from(source.clone())
            .project(vec![f.call(vec![col("x")]).alias("f_output")])
            .unwrap()
            .build()
            .unwrap();

        let filter_on_proj = LogicalPlanBuilder::from(proj)
            .filter(p.call(vec![col("f_output")]))
            .unwrap()
            .build()
            .unwrap();

        // Verify structure
        assert!(matches!(filter_on_proj, LogicalPlan::Filter(_)));
        if let LogicalPlan::Filter(filter) = &filter_on_proj {
            assert!(matches!(filter.input.as_ref(), LogicalPlan::Projection(_)));
        }

        // Note: Full pattern matching with abstract functions is complex
    }

    #[test]
    fn test_filter_project_transpose_pattern_validation() {
        // Validate the pattern structure
        let rule = FilterProjectTransposeRule;
        let pattern = rule.from();
        let replacement = rule.to();

        // Pattern should be: source.project(f).filter(P)
        assert!(matches!(pattern.plan, LogicalPlan::Filter(_)));
        if let LogicalPlan::Filter(filter) = &pattern.plan {
            assert!(matches!(filter.input.as_ref(), LogicalPlan::Projection(_)));
        }

        // Replacement should be: source.filter(P').project(f)
        assert!(matches!(replacement.plan, LogicalPlan::Projection(_)));
        if let LogicalPlan::Projection(proj) = &replacement.plan {
            assert!(matches!(proj.input.as_ref(), LogicalPlan::Filter(_)));
        }
    }
}

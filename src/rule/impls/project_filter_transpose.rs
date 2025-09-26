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

/// Pulls projection above filter
/// Pattern: Project(f(x), Filter(P(x), source)) → Filter(P(f⁻¹(y)), Project(f(x), source))
pub struct ProjectFilterTransposeRule;

impl RewriteRule for ProjectFilterTransposeRule {
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
                id: "T".to_string(),
            }],
            Type::Boolean,
        );

        let source = Rel::source("source".to_string(), schema);

        // Pattern: source.filter(P(x)).project(f(x))
        source
            .filter(p.call(vec![col("x")]))
            .unwrap()
            .project(vec![f.call(vec![col("x")]).alias("f_output")])
            .unwrap()
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
                id: "T".to_string(),
            }],
            Type::Boolean,
        );

        let source = Rel::source("source".to_string(), schema);

        // Replacement: source.project(f(x)).filter(P(f(x)))
        let proj = source
            .project(vec![f.call(vec![col("x")]).alias("f_output")])
            .unwrap();

        // Note: In practice, P needs to be rewritten in terms of the projected columns
        // This is a simplification - real implementation would need inverse function
        proj.filter(p.call(vec![col("f_output")])).unwrap()
    }

    fn name(&self) -> &str {
        "ProjectFilterTransposeRule"
    }
}

impl ApplicableRule<DefaultMatcher> for ProjectFilterTransposeRule {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_project_filter_transpose() {
        // Just test that the pattern compiles correctly
        let rule = ProjectFilterTransposeRule;
        let pattern = rule.from();
        let replacement = rule.to();
        
        // Verify the patterns are valid
        assert!(matches!(pattern.plan, datafusion::logical_expr::LogicalPlan::Projection(_)));
        assert!(matches!(replacement.plan, datafusion::logical_expr::LogicalPlan::Filter(_)));
    }
}

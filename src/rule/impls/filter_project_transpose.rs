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
    fn test_filter_project_transpose() {
        // Just test that the pattern compiles correctly
        // Real matching would require plans with abstract functions
        let rule = FilterProjectTransposeRule;
        let pattern = rule.from();
        let replacement = rule.to();

        // Verify the patterns are valid plans
        assert!(matches!(pattern.plan, LogicalPlan::Filter(_)));
        assert!(matches!(replacement.plan, LogicalPlan::Projection(_)));
    }
}

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
    use crate::rule::test::utils::test_table;
    use datafusion::logical_expr::{LogicalPlanBuilder, col};

    #[test]
    fn test_project_remove_identity() {
        let source = test_table();

        // Identity projection: all columns in same order
        let input = LogicalPlanBuilder::from(source.clone())
            .project(vec![col("a"), col("b")])
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectRemoveRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, source);
    }

    #[test]
    fn test_no_match_reordered() {
        let source = test_table();

        // Reordered columns - should not match
        let plan = LogicalPlanBuilder::from(source)
            .project(vec![col("b"), col("a")])
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectRemoveRule;
        assert!(rule.try_apply(&plan).is_err());
    }

    #[test]
    fn test_no_match_subset() {
        let source = test_table();

        // Subset of columns - should not match
        let plan = LogicalPlanBuilder::from(source)
            .project(vec![col("a")])
            .unwrap()
            .build()
            .unwrap();

        let rule = ProjectRemoveRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}

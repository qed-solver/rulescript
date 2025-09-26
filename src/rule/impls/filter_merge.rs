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

/// Merges two consecutive filters into one with AND
/// Pattern: source.filter(P).filter(Q) → source.filter(P AND Q)
pub struct FilterMergeRule;

impl RewriteRule for FilterMergeRule {
    fn from(&self) -> Rel {
        // Create a generic schema with a single field that can match any schema
        let schema = Schema {
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic {
                    id: "T".to_string(),
                },
                nullable: true, // nullable so it can match any field
            }],
        };

        // Create abstract predicates P and Q with explicit types
        let p = Function::new(
            "P".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Boolean,
        );
        let q = Function::new(
            "Q".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Boolean,
        );

        // Pattern: source.filter(P).filter(Q)
        let source = Rel::source("source".to_string(), schema);
        source
            .filter(p.call(vec![col("col")]))
            .unwrap()
            .filter(q.call(vec![col("col")]))
            .unwrap()
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

        let p = Function::new(
            "P".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Boolean,
        );
        let q = Function::new(
            "Q".to_string(),
            vec![Type::Generic {
                id: "T".to_string(),
            }],
            Type::Boolean,
        );

        // Replacement: source.filter(P AND Q)
        let source = Rel::source("source".to_string(), schema);
        source
            .filter(p.call(vec![col("col")]).and(q.call(vec![col("col")])))
            .unwrap()
    }

    fn name(&self) -> &str {
        "FilterMergeRule"
    }
}

impl ApplicableRule<DefaultMatcher> for FilterMergeRule {}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rule::test::utils::test_table;
    use datafusion::logical_expr::{LogicalPlanBuilder, col, lit};

    #[test]
    fn test_filter_merge() {
        let source = test_table();

        // Input: source.filter(a > 1).filter(b < 10)
        let input = LogicalPlanBuilder::from(source.clone())
            .filter(col("a").gt(lit(1)))
            .unwrap()
            .filter(col("b").lt(lit(10)))
            .unwrap()
            .build()
            .unwrap();

        // Expected: source.filter(a > 1 AND b < 10)
        let expected = LogicalPlanBuilder::from(source)
            .filter(col("a").gt(lit(1)).and(col("b").lt(lit(10))))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterMergeRule;
        let result = rule.try_apply(&input).unwrap();
        assert_eq!(result, expected);
    }

    #[test]
    fn test_no_match_single_filter() {
        let source = test_table();
        let plan = LogicalPlanBuilder::from(source)
            .filter(col("a").gt(lit(1)))
            .unwrap()
            .build()
            .unwrap();

        let rule = FilterMergeRule;
        assert!(rule.try_apply(&plan).is_err());
    }
}

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
        // Create a generic schema for the source
        let schema = Schema {
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic {
                    id: "T".to_string(),
                },
                nullable: true,
            }],
        };

        // Create abstract predicates P and Q with explicit types
        let p = Function::new(
            "P".to_string(),
            vec![Type::Generic { id: "T".to_string() }],
            Type::Boolean,
        );
        let q = Function::new(
            "Q".to_string(),
            vec![Type::Generic { id: "T".to_string() }],
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
        // Create same schema and predicates
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
            vec![Type::Generic { id: "T".to_string() }],
            Type::Boolean,
        );
        let q = Function::new(
            "Q".to_string(),
            vec![Type::Generic { id: "T".to_string() }],
            Type::Boolean,
        );

        // Replacement: source.filter(P AND Q)
        let source = Rel::source("source".to_string(), schema);
        source
            .filter(
                p.call(vec![col("col")])
                    .and(q.call(vec![col("col")])),
            )
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
    use crate::rule::test::utils::*;
    use datafusion::logical_expr::{LogicalPlan, LogicalPlanBuilder};

    #[tokio::test]
    async fn test_filter_merge_basic() {
        // Create test input: source.filter(age > 25).filter(salary > 50000)
        let source = test_table_scan("employees").await;
        let first_predicate = gt_expr("age", lit(25));
        let second_predicate = gt_expr("salary", lit(50000.0));
        
        let input_plan = LogicalPlanBuilder::from(source.clone())
            .filter(first_predicate.clone())
            .unwrap()
            .filter(second_predicate.clone())
            .unwrap()
            .build()
            .unwrap();

        // Create expected output: source.filter(first AND second)
        let expected_plan = LogicalPlanBuilder::from(source)
            .filter(first_predicate.and(second_predicate))
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = FilterMergeRule;
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
    async fn test_filter_merge_complex_predicates() {
        // Create test input with complex predicates
        let source = test_table_scan("employees").await;
        
        let first_predicate = and_expr(
            gt_expr("age", lit(25)),
            lt_expr("age", lit(65)),
        );
        let second_predicate = or_expr(
            eq_expr("active", lit(true)),
            gt_expr("salary", lit(100000.0)),
        );
        
        let input_plan = LogicalPlanBuilder::from(source.clone())
            .filter(first_predicate.clone())
            .unwrap()
            .filter(second_predicate.clone())
            .unwrap()
            .build()
            .unwrap();

        // Expected: source.filter(first_predicate AND second_predicate)
        let expected_plan = LogicalPlanBuilder::from(source)
            .filter(first_predicate.and(second_predicate))
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = FilterMergeRule;
        let result = rule.try_apply(&input_plan);

        assert!(result.is_ok(), "Rule should apply to complex predicates");
        let actual_plan = result.unwrap();
        
        assert_eq!(
            actual_plan, expected_plan,
            "Transformed plan does not match expected.\nActual:\n{:?}\n\nExpected:\n{:?}",
            actual_plan, expected_plan
        );
    }

    #[tokio::test]
    async fn test_filter_merge_no_match() {
        // Create test input with only one filter
        let source = test_table_scan("employees").await;
        let plan = LogicalPlanBuilder::from(source.clone())
            .filter(gt_expr("age", lit(25)))
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = FilterMergeRule;
        let result = rule.try_apply(&plan);

        assert!(result.is_err(), "Rule should not match single filter");
    }

    use datafusion::logical_expr::lit;
}
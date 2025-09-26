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

/// Pushes a filter below a projection when possible
/// Pattern: source.project(f).filter(P) → source.filter(P').project(f)
/// where P' is P with expressions substituted
pub struct FilterProjectTransposeRule;

impl RewriteRule for FilterProjectTransposeRule {
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

        // Create abstract functions with explicit types
        let f = Function::new(
            "f".to_string(),
            vec![
                Type::Generic {
                    id: "T1".to_string(),
                },
                Type::Generic {
                    id: "T2".to_string(),
                },
            ],
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

        // Pattern: source.project(f(a, b)).filter(P(projected_col))
        let source = Rel::source("source".to_string(), schema);
        source
            .project(vec![f.call(vec![col("a"), col("b")])])
            .unwrap()
            .filter(p.call(vec![col("f")])) // Filter on projected column
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

        // Create abstract functions with explicit types
        let f = Function::new(
            "f".to_string(),
            vec![
                Type::Generic {
                    id: "T1".to_string(),
                },
                Type::Generic {
                    id: "T2".to_string(),
                },
            ],
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

        // Replacement: source.filter(P(f(a, b))).project(f(a, b))
        // The predicate is pushed down and operates on the original columns
        let source = Rel::source("source".to_string(), schema);
        source
            .filter(p.call(vec![f.call(vec![col("a"), col("b")])]))
            .unwrap()
            .project(vec![f.call(vec![col("a"), col("b")])])
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
    use crate::rule::test::utils::*;
    use datafusion::logical_expr::LogicalPlanBuilder;

    #[tokio::test]
    async fn test_filter_project_transpose_basic() {
        // Create test: source.project(age + 10).filter(result > 30)
        // Should become: source.filter(age + 10 > 30).project(age + 10)
        let source = test_table_scan("employees").await;

        // Build input plan: project then filter
        let input_plan = LogicalPlanBuilder::from(source.clone())
            .project(vec![
                col("id"),
                (col("age") + lit(10)).alias("adjusted_age"),
            ])
            .unwrap()
            .filter(col("adjusted_age").gt(lit(30)))
            .unwrap()
            .build()
            .unwrap();

        // Build expected output: filter then project
        // The filter predicate should be rewritten in terms of base columns
        let expected_plan = LogicalPlanBuilder::from(source)
            .filter((col("age") + lit(10)).gt(lit(30)))
            .unwrap()
            .project(vec![
                col("id"),
                (col("age") + lit(10)).alias("adjusted_age"),
            ])
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = FilterProjectTransposeRule;
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
    async fn test_filter_project_transpose_complex_predicate() {
        // Test with complex predicate involving multiple projected columns
        let source = test_table_scan("employees").await;

        let input_plan = LogicalPlanBuilder::from(source.clone())
            .project(vec![
                (col("age") * lit(2)).alias("double_age"),
                (col("salary") / lit(1000.0)).alias("salary_k"),
            ])
            .unwrap()
            .filter(
                col("double_age")
                    .gt(lit(50))
                    .and(col("salary_k").lt(lit(100.0))),
            )
            .unwrap()
            .build()
            .unwrap();

        // Expected: filter with rewritten predicates, then project
        // double_age > 50 becomes (age * 2) > 50
        // salary_k < 100 becomes (salary / 1000) < 100
        let expected_plan = LogicalPlanBuilder::from(source)
            .filter(
                (col("age") * lit(2))
                    .gt(lit(50))
                    .and((col("salary") / lit(1000.0)).lt(lit(100.0))),
            )
            .unwrap()
            .project(vec![
                (col("age") * lit(2)).alias("double_age"),
                (col("salary") / lit(1000.0)).alias("salary_k"),
            ])
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&input_plan);

        assert!(result.is_ok(), "Should transpose complex predicates");
        let actual_plan = result.unwrap();

        assert_eq!(
            actual_plan, expected_plan,
            "Transformed plan does not match expected.\nActual:\n{:?}\n\nExpected:\n{:?}",
            actual_plan, expected_plan
        );
    }

    #[tokio::test]
    async fn test_filter_project_transpose_no_match() {
        // Test with no filter - should not match
        let source = test_table_scan("employees").await;

        let plan = LogicalPlanBuilder::from(source.clone())
            .project(vec![col("id"), col("name")])
            .unwrap()
            .build()
            .unwrap();

        // Apply the rule
        let rule = FilterProjectTransposeRule;
        let result = rule.try_apply(&plan);

        assert!(result.is_err(), "Should not match when no filter present");
    }

    use datafusion::logical_expr::lit;
}

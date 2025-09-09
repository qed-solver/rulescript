use datafusion::{
    logical_expr::{BinaryExpr, Operator, builder::LogicalPlanBuilder, col, lit},
    prelude::Expr,
};

use rulescript::{Field, Rel, RewriteRule, Schema, Type};

struct FilterMergeRule;

impl RewriteRule for FilterMergeRule {
    fn pattern(&self) -> Rel {
        // Create abstract schema using struct literal syntax (no .new() calls)
        let schema = Schema {
            fields: vec![
                Field {
                    name: "id".to_string(),
                    data_type: Type {
                        id: "int".to_string(),
                    },
                    nullable: false,
                },
                Field {
                    name: "name".to_string(),
                    data_type: Type {
                        id: "string".to_string(),
                    },
                    nullable: true,
                },
            ],
        };

        // Create the source pattern using our library
        let source_rel = Rel::source("table".to_string(), schema);

        // For demonstration, use concrete predicates instead of abstract functions
        // In the real system, these would be pattern variables
        let inner_predicate = col("id").gt(lit(5)); // id > 5
        let outer_predicate = col("name").is_not_null(); // name IS NOT NULL

        // Pattern: source.filter(inner).filter(outer)
        let inner_filter = LogicalPlanBuilder::from(source_rel.plan)
            .filter(inner_predicate)
            .unwrap()
            .build()
            .unwrap();

        let outer_filter = LogicalPlanBuilder::from(inner_filter)
            .filter(outer_predicate)
            .unwrap()
            .build()
            .unwrap();

        Rel { plan: outer_filter }
    }

    fn replacement(&self) -> Rel {
        // Create the same source as in pattern
        let schema = Schema {
            fields: vec![
                Field {
                    name: "id".to_string(),
                    data_type: Type {
                        id: "int".to_string(),
                    },
                    nullable: false,
                },
                Field {
                    name: "name".to_string(),
                    data_type: Type {
                        id: "string".to_string(),
                    },
                    nullable: true,
                },
            ],
        };

        let source_rel = Rel::source("table".to_string(), schema);

        // Recreate the same predicates
        let inner_predicate = col("id").gt(lit(5)); // id > 5
        let outer_predicate = col("name").is_not_null(); // name IS NOT NULL

        // Replacement: source.filter(inner AND outer)
        let combined_predicate = Expr::BinaryExpr(BinaryExpr {
            left: Box::new(inner_predicate),
            op: Operator::And,
            right: Box::new(outer_predicate),
        });

        let merged_filter = LogicalPlanBuilder::from(source_rel.plan)
            .filter(combined_predicate)
            .unwrap()
            .build()
            .unwrap();

        Rel {
            plan: merged_filter,
        }
    }
}

fn main() {
    println!("FilterMerge Rule Example");

    let rule = FilterMergeRule;

    // Show the pattern
    let pattern = rule.pattern();
    println!("\nPattern (source.filter(inner).filter(outer)):");
    println!("{:?}", pattern.plan);

    // Show the replacement
    let replacement = rule.replacement();
    println!("\nReplacement (source.filter(inner AND outer)):");
    println!("{:?}", replacement.plan);

    println!("\nRule successfully constructed using direct DataFusion APIs!");
    println!("In a real system, the predicates would be abstract pattern variables,");
    println!("not concrete expressions like 'id > 5' and 'name IS NOT NULL'.");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_filter_merge_construction() {
        let rule = FilterMergeRule;

        // Verify we can construct both pattern and replacement
        let _pattern = rule.pattern();
        let _replacement = rule.replacement();

        // In a real system, we would verify equivalence here
        assert!(true, "FilterMerge rule constructs successfully");
    }
}

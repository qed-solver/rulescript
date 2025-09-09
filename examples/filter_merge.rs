use datafusion::{
    logical_expr::{BinaryExpr, Operator, builder::LogicalPlanBuilder},
    prelude::{Expr, col},
};

use rulescript::{Field, Function, Rel, RewriteRule, Schema, Type};

struct FilterMergeRule;

impl RewriteRule for FilterMergeRule {
    fn pattern(&self) -> Rel {
        // Create abstract schema with one abstract column (can represent arbitrary inputs)
        let schema = Schema {
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic { id: "T".to_string() },
                nullable: false,
            }],
        };

        // Create the source pattern using our library
        let source_rel = Rel::source("table".to_string(), schema);

        // Create abstract predicates that take the abstract column as argument
        // P(col) and Q(col) represent predicates over the source relation
        let inner_predicate_func = Function::boolean_predicate("P".to_string(), 1); // Takes 1 abstract column
        let outer_predicate_func = Function::boolean_predicate("Q".to_string(), 1); // Takes 1 abstract column

        // Create column reference as argument to the predicates
        let col_ref = col("col");

        let inner_predicate = inner_predicate_func.call(vec![col_ref.clone()]);
        let outer_predicate = outer_predicate_func.call(vec![col_ref.clone()]);

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
        // Create the same abstract schema (must match pattern for correctness)
        let schema = Schema {
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic { id: "T".to_string() },
                nullable: false,
            }],
        };

        let source_rel = Rel::source("table".to_string(), schema);

        // Recreate the same abstract predicates (must use same names for correctness)
        let inner_predicate_func = Function::boolean_predicate("P".to_string(), 1); // Takes 1 abstract column
        let outer_predicate_func = Function::boolean_predicate("Q".to_string(), 1); // Takes 1 abstract column

        // Same column reference as argument
        let col_ref = col("col");

        let inner_predicate = inner_predicate_func.call(vec![col_ref.clone()]);
        let outer_predicate = outer_predicate_func.call(vec![col_ref]);

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

    // DataFusion's LogicalPlan implements Display for pretty printing

    // Show the pattern
    let pattern = rule.pattern();
    println!("\nPattern (source.filter(P).filter(Q)):");
    println!("{}", pattern.plan);

    // Show the replacement
    let replacement = rule.replacement();
    println!("\nReplacement (source.filter(P AND Q)):");
    println!("{}", replacement.plan);
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

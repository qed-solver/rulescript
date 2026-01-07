//! TPC-H query optimization example
//!
//! Tests optimization rules on TPC-H queries:
//! - `FilterIntoJoin`: Pushes filter predicates into cross join conditions
//! - `JoinConditionPush`: Pushes single-table predicates from join condition to input filters
//!
//! ## Query Categorization
//!
//! ### OPTIMIZED (15 queries)
//!
//! Multi-table comma-separated FROM clauses (produces CrossJoins):
//!
//! | Query | Tables |
//! |-------|--------|
//! | Q3 | customer, orders, lineitem |
//! | Q5 | customer, orders, lineitem, supplier, nation, region |
//! | Q7 | subquery: supplier, lineitem, orders, customer, nation x2 |
//! | Q8 | subquery: part, supplier, lineitem, orders, customer, nation x2, region |
//! | Q9 | subquery: part, supplier, lineitem, partsupp, orders, nation |
//! | Q10 | customer, orders, lineitem, nation |
//! | Q11 | partsupp, supplier, nation |
//! | Q12 | orders, lineitem |
//! | Q14 | lineitem, part |
//! | Q15 | supplier, revenue (CTE) |
//! | Q16 | partsupp, part |
//! | Q18 | customer, orders, lineitem |
//! | Q19 | lineitem, part |
//! | Q20 | supplier, nation |
//! | Q21 | supplier, lineitem, orders, nation |
//!
//! ### UNCHANGED (5 queries)
//!
//! | Query | Reason |
//! |-------|--------|
//! | Q1 | Single table (lineitem) |
//! | Q4 | Single table (orders) with EXISTS subquery |
//! | Q6 | Single table (lineitem) |
//! | Q13 | LEFT OUTER JOIN (not cross join) |
//! | Q22 | Derived table from single table (customer) |
//!
//! ### ERROR (2 queries)
//!
//! Queries with correlated subqueries in filter predicates. The rule matches and
//! transforms successfully, but the resulting plan is invalid because DataFusion
//! doesn't support correlated subqueries in join conditions.
//!
//! | Query | Reason |
//! |-------|--------|
//! | Q2 | Correlated subquery: `outer_ref(part.p_partkey)` |
//! | Q17 | Correlated subquery: `outer_ref(part.p_partkey)` |
//!
//! ## Usage
//!
//! Run with: cargo run --example tpch_optimize
//! Run with verbose: cargo run --example tpch_optimize -- -v

mod tpch;

use datafusion::{
    optimizer::{Optimizer, OptimizerContext},
    prelude::*,
};
use rulescript::rule::{
    RuleWrapper,
    impls::{FilterIntoJoinRule, JoinLeftConditionPushRule, JoinRightConditionPushRule},
};
use std::sync::Arc;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let ctx = SessionContext::new();

    // Register all TPC-H tables
    println!("Registering TPC-H tables...");
    tpch::register_tables(&ctx).await;
    println!("  Registered 8 tables\n");

    // Create optimizer with our rules
    let rules: Vec<Arc<dyn datafusion::optimizer::OptimizerRule + Send + Sync>> = vec![
        Arc::new(RuleWrapper::new(FilterIntoJoinRule)),
        Arc::new(RuleWrapper::new(JoinLeftConditionPushRule)),
        Arc::new(RuleWrapper::new(JoinRightConditionPushRule)),
    ];
    let optimizer = Optimizer::with_rules(rules);
    let config = OptimizerContext::new();

    println!("Rules: FilterIntoJoin, JoinLeftConditionPush, JoinRightConditionPush\n");
    println!("========================================\n");

    let verbose = std::env::args().any(|arg| arg == "-v" || arg == "--verbose");

    let mut optimized_count = 0;
    let mut unchanged_count = 0;
    let mut error_count = 0;

    for (i, sql) in tpch::ALL_QUERIES.iter().enumerate() {
        let name = format!("Q{}", i + 1);

        // Parse the SQL
        let df = match ctx.sql(sql).await {
            Ok(df) => df,
            Err(e) => {
                println!("[ERROR] {} - Parse error: {}", name, e);
                error_count += 1;
                continue;
            }
        };

        let original_plan = df.logical_plan().clone();

        // Apply optimization
        match optimizer.optimize(original_plan.clone(), &config, |_, _| {}) {
            Ok(optimized_plan) => {
                if optimized_plan == original_plan {
                    println!("[UNCHANGED] {}", name);
                    unchanged_count += 1;
                } else {
                    println!("[OPTIMIZED] {}", name);
                    optimized_count += 1;

                    if verbose {
                        println!("\n  BEFORE:");
                        for line in original_plan.display_indent().to_string().lines() {
                            println!("    {}", line);
                        }
                        println!("\n  AFTER:");
                        for line in optimized_plan.display_indent().to_string().lines() {
                            println!("    {}", line);
                        }
                        println!();
                    }
                }
            }
            Err(e) => {
                println!("[ERROR] {} - Optimization error: {}", name, e);
                error_count += 1;
            }
        }
    }

    println!("\n========================================");
    println!(
        "Results: {} optimized, {} unchanged, {} errors",
        optimized_count, unchanged_count, error_count
    );
    println!("========================================");

    Ok(())
}

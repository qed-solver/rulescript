//! TPC-H Benchmark - compares execution with/without RuleScript optimization
//!
//! Usage:
//!   cargo run --release --example tpch_benchmark -- -p data/tpch-sf1
//!   cargo run --release --example tpch_benchmark -- -p data/tpch-sf1 -q 3
//!   cargo run --release --example tpch_benchmark -- -p data/tpch-sf1 --compare-plans

mod tpch;

use std::{path::PathBuf, sync::Arc, time::Instant};

use datafusion::{
    arrow::datatypes::{DataType, Field, Schema},
    datasource::file_format::csv::CsvFormat,
    datasource::listing::{ListingOptions, ListingTable, ListingTableConfig, ListingTableUrl},
    error::Result,
    execution::SessionStateBuilder,
    optimizer::OptimizerRule,
    prelude::*,
};
use rulescript::rule::{
    RuleWrapper,
    impls::{FilterIntoJoinRule, JoinLeftConditionPushRule, JoinRightConditionPushRule},
};

const TPCH_TABLES: &[&str] = &[
    "part", "supplier", "partsupp", "customer", "orders", "lineitem", "nation", "region",
];

fn get_tbl_schema(table: &str) -> Schema {
    let mut fields = tpch::get_schema(table).fields().to_vec();
    fields.push(Arc::new(Field::new("_placeholder", DataType::Utf8, true)));
    Schema::new(fields)
}

async fn register_tables(ctx: &SessionContext, path: &PathBuf) -> Result<()> {
    for table in TPCH_TABLES {
        let table_path = ListingTableUrl::parse(format!("{}/{}.tbl", path.display(), table))?;
        let format = CsvFormat::default()
            .with_delimiter(b'|')
            .with_has_header(false);
        let options = ListingOptions::new(Arc::new(format)).with_file_extension(".tbl");
        let schema = Arc::new(get_tbl_schema(table));
        let config = ListingTableConfig::new(table_path)
            .with_listing_options(options)
            .with_schema(schema);
        ctx.register_table(*table, Arc::new(ListingTable::try_new(config)?))?;
    }
    Ok(())
}

async fn run_query(ctx: &SessionContext, sql: &str) -> Result<(f64, usize)> {
    let start = Instant::now();
    let df = ctx.sql(sql).await?;
    let batches = df.collect().await?;
    let rows: usize = batches.iter().map(|b| b.num_rows()).sum();
    Ok((start.elapsed().as_secs_f64() * 1000.0, rows))
}

/// Create SessionContext with RuleScript rules added to DataFusion's default optimizer
fn create_rulescript_context() -> SessionContext {
    // Get DataFusion's default rules
    let mut rules = datafusion::optimizer::Optimizer::new().rules;

    // Prepend our RuleScript rules (so they run first)
    let rulescript_rules: Vec<Arc<dyn OptimizerRule + Send + Sync>> = vec![
        Arc::new(RuleWrapper::new(FilterIntoJoinRule)),
        Arc::new(RuleWrapper::new(JoinLeftConditionPushRule)),
        Arc::new(RuleWrapper::new(JoinRightConditionPushRule)),
    ];

    let mut all_rules = rulescript_rules;
    all_rules.append(&mut rules);

    let state = SessionStateBuilder::new()
        .with_default_features()
        .with_optimizer_rules(all_rules)
        .build();
    SessionContext::new_with_state(state)
}

#[tokio::main]
async fn main() -> Result<()> {
    let args: Vec<String> = std::env::args().collect();

    let mut path: Option<PathBuf> = None;
    let mut query: Option<usize> = None;
    let mut iterations = 3;
    let mut compare_plans_mode = false;

    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "-p" | "--path" => {
                i += 1;
                path = Some(PathBuf::from(&args[i]));
            }
            "-q" | "--query" => {
                i += 1;
                query = Some(args[i].parse().unwrap());
            }
            "-n" | "--iterations" => {
                i += 1;
                iterations = args[i].parse().unwrap();
            }
            "--compare-plans" => {
                compare_plans_mode = true;
            }
            _ => {}
        }
        i += 1;
    }

    let path =
        path.expect("Usage: -p <data_path> [-q <query>] [-n <iterations>] [--compare-plans]");

    // Baseline: default DataFusion
    let baseline_ctx = SessionContext::new();
    register_tables(&baseline_ctx, &path).await?;

    // RuleScript: DataFusion + our rules integrated
    let rulescript_ctx = create_rulescript_context();
    register_tables(&rulescript_ctx, &path).await?;

    let queries: Vec<usize> = match query {
        Some(q) => vec![q],
        None => (1..=22).collect(),
    };

    if compare_plans_mode {
        println!("Comparing optimized plans: Baseline vs RuleScript\n");

        for q in queries {
            let sql = tpch::ALL_QUERIES.get(q - 1).unwrap();

            // Get optimized plans from both contexts
            let baseline_plan = baseline_ctx.sql(sql).await?.into_optimized_plan()?;
            let rulescript_plan = rulescript_ctx.sql(sql).await?.into_optimized_plan()?;

            let baseline_str = format!("{}", baseline_plan.display_indent());
            let rulescript_str = format!("{}", rulescript_plan.display_indent());

            if baseline_str == rulescript_str {
                println!("Q{:2}: SAME", q);
            } else {
                println!("Q{:2}: DIFFERENT", q);
                println!("\n--- Baseline ---");
                println!("{}", baseline_str);
                println!("\n--- RuleScript ---");
                println!("{}", rulescript_str);
                println!();
            }
        }
    } else {
        println!("TPC-H Benchmark (iterations={})", iterations);
        println!("Data: {}", path.display());
        println!("RuleScript rules added to DataFusion optimizer\n");

        println!(
            "{:>5} {:>12} {:>12} {:>8} {:>8}",
            "Query", "Baseline", "RuleScript", "Speedup", "Rows"
        );
        println!("{}", "-".repeat(55));

        for q in queries {
            let sql = tpch::ALL_QUERIES.get(q - 1).unwrap();

            let mut base_times = Vec::new();
            let mut opt_times = Vec::new();
            let mut rows = 0;
            let mut error = None;

            for _ in 0..iterations {
                match run_query(&baseline_ctx, sql).await {
                    Ok((ms, r)) => {
                        base_times.push(ms);
                        rows = r;
                    }
                    Err(e) => {
                        error = Some(format!("baseline: {}", e));
                        break;
                    }
                }

                match run_query(&rulescript_ctx, sql).await {
                    Ok((ms, _)) => {
                        opt_times.push(ms);
                    }
                    Err(e) => {
                        error = Some(format!("rulescript: {}", e));
                        break;
                    }
                }
            }

            if let Some(e) = error {
                println!("Q{:>2} error: {}", q, e);
            } else if base_times.len() == iterations && opt_times.len() == iterations {
                let base_avg = base_times.iter().sum::<f64>() / iterations as f64;
                let opt_avg = opt_times.iter().sum::<f64>() / iterations as f64;
                let speedup = base_avg / opt_avg;
                println!(
                    "Q{:>2} {:>10.1}ms {:>10.1}ms {:>7.2}x {:>8}",
                    q, base_avg, opt_avg, speedup, rows
                );
            }
        }
    }

    Ok(())
}

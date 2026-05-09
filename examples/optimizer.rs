//! Interactive optimizer demonstration with rule selection
//!
//! This example provides an interactive SQL REPL where you can:
//! - Select which optimization rules to apply
//! - Switch rules during the session
//! - See how rules transform query plans
//!
//! Run with: cargo run --example optimizer

mod optimizer_repl;

use optimizer_repl::OptimizerRepl;

#[tokio::main]
async fn main() {
    let mut repl = OptimizerRepl::new().await;
    repl.run().await;
}

//! TPC-H benchmark support for RuleScript
//!
//! This module provides TPC-H table schemas and queries for testing
//! optimizer rules against real-world analytical query patterns.
//!
//! ## Contents
//!
//! - `schema`: TPC-H table definitions (8 tables)
//! - `queries`: All 22 TPC-H queries with parameters filled in
//!
//! ## Usage
//!
//! ```ignore
//! use tpch::{register_tables, Q1, Q3};
//!
//! let ctx = SessionContext::new();
//! register_tables(&ctx).await;
//!
//! let df = ctx.sql(Q1).await?;
//! ```
//!
//! ## Source
//!
//! Table schemas and queries are derived from the official TPC-H 3.0.1 specification.
//! - Schema: `TPC-H V3.0.1/dbgen/dss.ddl`
//! - Queries: `TPC-H V3.0.1/dbgen/queries/{1-22}.sql`
//!
//! ## License
//!
//! TPC-H is a trademark of the Transaction Processing Performance Council (TPC).
//! The TPC-H specification and associated materials are used here for academic
//! and research purposes in accordance with the TPC EULA v2.2.
//!
//! THE TPC SOFTWARE IS AVAILABLE WITHOUT CHARGE FROM TPC.
//! See <https://www.tpc.org/tpch/> for official materials.

pub mod queries;
pub mod schema;

pub use queries::*;
#[allow(unused_imports)]
pub use schema::get_schema;
#[allow(unused_imports)]
pub use schema::register_tables;

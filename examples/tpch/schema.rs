//! TPC-H table schema definitions
//!
//! Defines the 8 TPC-H tables according to the official TPC-H 3.0.1 specification.
//! Source: `TPC-H V3.0.1/dbgen/dss.ddl`
//!
//! TPC-H is a trademark of the Transaction Processing Performance Council (TPC).
//! THE TPC SOFTWARE IS AVAILABLE WITHOUT CHARGE FROM TPC.

use datafusion::{
    arrow::datatypes::{DataType, Field, Schema},
    datasource::empty::EmptyTable,
    prelude::SessionContext,
};
use std::sync::Arc;

/// Register all TPC-H tables with the given session context.
///
/// Tables are registered as empty tables (schema only) since we only
/// need the schema for query planning and optimization testing.
#[allow(dead_code)]
pub async fn register_tables(ctx: &SessionContext) {
    let tables = [
        ("nation", nation_schema()),
        ("region", region_schema()),
        ("part", part_schema()),
        ("supplier", supplier_schema()),
        ("partsupp", partsupp_schema()),
        ("customer", customer_schema()),
        ("orders", orders_schema()),
        ("lineitem", lineitem_schema()),
    ];

    for (name, schema) in tables {
        let empty_table = EmptyTable::new(Arc::new(schema));
        ctx.register_table(name, Arc::new(empty_table))
            .expect("Failed to register TPC-H table");
    }
}

/// NATION table schema
/// ```sql
/// CREATE TABLE NATION (
///     N_NATIONKEY  INTEGER NOT NULL,
///     N_NAME       CHAR(25) NOT NULL,
///     N_REGIONKEY  INTEGER NOT NULL,
///     N_COMMENT    VARCHAR(152)
/// );
/// ```
fn nation_schema() -> Schema {
    Schema::new(vec![
        Field::new("n_nationkey", DataType::Int32, false),
        Field::new("n_name", DataType::Utf8, false),
        Field::new("n_regionkey", DataType::Int32, false),
        Field::new("n_comment", DataType::Utf8, true),
    ])
}

/// REGION table schema
/// ```sql
/// CREATE TABLE REGION (
///     R_REGIONKEY  INTEGER NOT NULL,
///     R_NAME       CHAR(25) NOT NULL,
///     R_COMMENT    VARCHAR(152)
/// );
/// ```
fn region_schema() -> Schema {
    Schema::new(vec![
        Field::new("r_regionkey", DataType::Int32, false),
        Field::new("r_name", DataType::Utf8, false),
        Field::new("r_comment", DataType::Utf8, true),
    ])
}

/// PART table schema
/// ```sql
/// CREATE TABLE PART (
///     P_PARTKEY     INTEGER NOT NULL,
///     P_NAME        VARCHAR(55) NOT NULL,
///     P_MFGR        CHAR(25) NOT NULL,
///     P_BRAND       CHAR(10) NOT NULL,
///     P_TYPE        VARCHAR(25) NOT NULL,
///     P_SIZE        INTEGER NOT NULL,
///     P_CONTAINER   CHAR(10) NOT NULL,
///     P_RETAILPRICE DECIMAL(15,2) NOT NULL,
///     P_COMMENT     VARCHAR(23) NOT NULL
/// );
/// ```
fn part_schema() -> Schema {
    Schema::new(vec![
        Field::new("p_partkey", DataType::Int32, false),
        Field::new("p_name", DataType::Utf8, false),
        Field::new("p_mfgr", DataType::Utf8, false),
        Field::new("p_brand", DataType::Utf8, false),
        Field::new("p_type", DataType::Utf8, false),
        Field::new("p_size", DataType::Int32, false),
        Field::new("p_container", DataType::Utf8, false),
        Field::new("p_retailprice", DataType::Decimal128(15, 2), false),
        Field::new("p_comment", DataType::Utf8, false),
    ])
}

/// SUPPLIER table schema
/// ```sql
/// CREATE TABLE SUPPLIER (
///     S_SUPPKEY     INTEGER NOT NULL,
///     S_NAME        CHAR(25) NOT NULL,
///     S_ADDRESS     VARCHAR(40) NOT NULL,
///     S_NATIONKEY   INTEGER NOT NULL,
///     S_PHONE       CHAR(15) NOT NULL,
///     S_ACCTBAL     DECIMAL(15,2) NOT NULL,
///     S_COMMENT     VARCHAR(101) NOT NULL
/// );
/// ```
fn supplier_schema() -> Schema {
    Schema::new(vec![
        Field::new("s_suppkey", DataType::Int32, false),
        Field::new("s_name", DataType::Utf8, false),
        Field::new("s_address", DataType::Utf8, false),
        Field::new("s_nationkey", DataType::Int32, false),
        Field::new("s_phone", DataType::Utf8, false),
        Field::new("s_acctbal", DataType::Decimal128(15, 2), false),
        Field::new("s_comment", DataType::Utf8, false),
    ])
}

/// PARTSUPP table schema
/// ```sql
/// CREATE TABLE PARTSUPP (
///     PS_PARTKEY     INTEGER NOT NULL,
///     PS_SUPPKEY     INTEGER NOT NULL,
///     PS_AVAILQTY    INTEGER NOT NULL,
///     PS_SUPPLYCOST  DECIMAL(15,2) NOT NULL,
///     PS_COMMENT     VARCHAR(199) NOT NULL
/// );
/// ```
fn partsupp_schema() -> Schema {
    Schema::new(vec![
        Field::new("ps_partkey", DataType::Int32, false),
        Field::new("ps_suppkey", DataType::Int32, false),
        Field::new("ps_availqty", DataType::Int32, false),
        Field::new("ps_supplycost", DataType::Decimal128(15, 2), false),
        Field::new("ps_comment", DataType::Utf8, false),
    ])
}

/// CUSTOMER table schema
/// ```sql
/// CREATE TABLE CUSTOMER (
///     C_CUSTKEY     INTEGER NOT NULL,
///     C_NAME        VARCHAR(25) NOT NULL,
///     C_ADDRESS     VARCHAR(40) NOT NULL,
///     C_NATIONKEY   INTEGER NOT NULL,
///     C_PHONE       CHAR(15) NOT NULL,
///     C_ACCTBAL     DECIMAL(15,2) NOT NULL,
///     C_MKTSEGMENT  CHAR(10) NOT NULL,
///     C_COMMENT     VARCHAR(117) NOT NULL
/// );
/// ```
fn customer_schema() -> Schema {
    Schema::new(vec![
        Field::new("c_custkey", DataType::Int32, false),
        Field::new("c_name", DataType::Utf8, false),
        Field::new("c_address", DataType::Utf8, false),
        Field::new("c_nationkey", DataType::Int32, false),
        Field::new("c_phone", DataType::Utf8, false),
        Field::new("c_acctbal", DataType::Decimal128(15, 2), false),
        Field::new("c_mktsegment", DataType::Utf8, false),
        Field::new("c_comment", DataType::Utf8, false),
    ])
}

/// ORDERS table schema
/// ```sql
/// CREATE TABLE ORDERS (
///     O_ORDERKEY       INTEGER NOT NULL,
///     O_CUSTKEY        INTEGER NOT NULL,
///     O_ORDERSTATUS    CHAR(1) NOT NULL,
///     O_TOTALPRICE     DECIMAL(15,2) NOT NULL,
///     O_ORDERDATE      DATE NOT NULL,
///     O_ORDERPRIORITY  CHAR(15) NOT NULL,
///     O_CLERK          CHAR(15) NOT NULL,
///     O_SHIPPRIORITY   INTEGER NOT NULL,
///     O_COMMENT        VARCHAR(79) NOT NULL
/// );
/// ```
fn orders_schema() -> Schema {
    Schema::new(vec![
        Field::new("o_orderkey", DataType::Int32, false),
        Field::new("o_custkey", DataType::Int32, false),
        Field::new("o_orderstatus", DataType::Utf8, false),
        Field::new("o_totalprice", DataType::Decimal128(15, 2), false),
        Field::new("o_orderdate", DataType::Date32, false),
        Field::new("o_orderpriority", DataType::Utf8, false),
        Field::new("o_clerk", DataType::Utf8, false),
        Field::new("o_shippriority", DataType::Int32, false),
        Field::new("o_comment", DataType::Utf8, false),
    ])
}

/// LINEITEM table schema
/// ```sql
/// CREATE TABLE LINEITEM (
///     L_ORDERKEY       INTEGER NOT NULL,
///     L_PARTKEY        INTEGER NOT NULL,
///     L_SUPPKEY        INTEGER NOT NULL,
///     L_LINENUMBER     INTEGER NOT NULL,
///     L_QUANTITY       DECIMAL(15,2) NOT NULL,
///     L_EXTENDEDPRICE  DECIMAL(15,2) NOT NULL,
///     L_DISCOUNT       DECIMAL(15,2) NOT NULL,
///     L_TAX            DECIMAL(15,2) NOT NULL,
///     L_RETURNFLAG     CHAR(1) NOT NULL,
///     L_LINESTATUS     CHAR(1) NOT NULL,
///     L_SHIPDATE       DATE NOT NULL,
///     L_COMMITDATE     DATE NOT NULL,
///     L_RECEIPTDATE    DATE NOT NULL,
///     L_SHIPINSTRUCT   CHAR(25) NOT NULL,
///     L_SHIPMODE       CHAR(10) NOT NULL,
///     L_COMMENT        VARCHAR(44) NOT NULL
/// );
/// ```
fn lineitem_schema() -> Schema {
    Schema::new(vec![
        Field::new("l_orderkey", DataType::Int32, false),
        Field::new("l_partkey", DataType::Int32, false),
        Field::new("l_suppkey", DataType::Int32, false),
        Field::new("l_linenumber", DataType::Int32, false),
        Field::new("l_quantity", DataType::Decimal128(15, 2), false),
        Field::new("l_extendedprice", DataType::Decimal128(15, 2), false),
        Field::new("l_discount", DataType::Decimal128(15, 2), false),
        Field::new("l_tax", DataType::Decimal128(15, 2), false),
        Field::new("l_returnflag", DataType::Utf8, false),
        Field::new("l_linestatus", DataType::Utf8, false),
        Field::new("l_shipdate", DataType::Date32, false),
        Field::new("l_commitdate", DataType::Date32, false),
        Field::new("l_receiptdate", DataType::Date32, false),
        Field::new("l_shipinstruct", DataType::Utf8, false),
        Field::new("l_shipmode", DataType::Utf8, false),
        Field::new("l_comment", DataType::Utf8, false),
    ])
}

/// Get schema for a TPC-H table by name
#[allow(dead_code)]
pub fn get_schema(table: &str) -> Schema {
    match table {
        "nation" => nation_schema(),
        "region" => region_schema(),
        "part" => part_schema(),
        "supplier" => supplier_schema(),
        "partsupp" => partsupp_schema(),
        "customer" => customer_schema(),
        "orders" => orders_schema(),
        "lineitem" => lineitem_schema(),
        _ => panic!("Unknown table: {}", table),
    }
}

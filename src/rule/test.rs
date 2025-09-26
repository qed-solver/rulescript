#[cfg(test)]
pub mod utils {
    use datafusion::{
        arrow::datatypes::{DataType, Field, Schema as ArrowSchema},
        common::DFSchema,
        logical_expr::{Expr, LogicalPlan, LogicalPlanBuilder, Operator, col, lit},
        prelude::SessionContext,
    };
    use std::sync::Arc;

    /// Create a simple test schema with common column types
    pub fn test_schema() -> Arc<ArrowSchema> {
        Arc::new(ArrowSchema::new(vec![
            Field::new("id", DataType::Int32, false),
            Field::new("name", DataType::Utf8, false),
            Field::new("age", DataType::Int32, true),
            Field::new("salary", DataType::Float64, true),
            Field::new("active", DataType::Boolean, false),
        ]))
    }

    /// Create a test table scan with the standard test schema
    pub async fn test_table_scan(table_name: &str) -> LogicalPlan {
        let ctx = SessionContext::new();
        let schema = test_schema();

        // Register a memory table with the schema
        ctx.register_csv(
            table_name,
            "data/test.csv", // This doesn't need to exist for planning
            datafusion::prelude::CsvReadOptions::default()
                .has_header(true)
                .schema(&schema),
        )
        .await
        .unwrap_or_else(|_| {
            // Fallback: create empty table scan
            ctx.register_table(
                table_name,
                Arc::new(datafusion::datasource::empty::EmptyTable::new(
                    schema.clone(),
                )),
            )
            .unwrap();
        });

        // Build a simple scan
        let df = ctx.table(table_name).await.unwrap();
        df.logical_plan().clone()
    }

    /// Create a test filter expression
    pub fn test_filter_expr(column: &str, op: Operator, value: i32) -> Expr {
        Expr::BinaryExpr(datafusion::logical_expr::BinaryExpr::new(
            Box::new(col(column)),
            op,
            Box::new(lit(value)),
        ))
    }

    /// Create a projection with specific columns
    pub fn project_cols(cols: Vec<&str>) -> Vec<Expr> {
        cols.into_iter().map(col).collect()
    }

    /// Create identity projection (all columns)
    pub fn identity_projection(schema: &DFSchema) -> Vec<Expr> {
        schema.fields().iter().map(|f| col(f.name())).collect()
    }

    /// Compare two logical plans for equality
    pub fn plans_equal(plan1: &LogicalPlan, plan2: &LogicalPlan) -> bool {
        plan1 == plan2
    }

    /// Build the expected output for a test and compare with actual
    pub fn assert_plan_equals(actual: &LogicalPlan, expected: &LogicalPlan) {
        assert_eq!(
            actual, expected,
            "Plans do not match.\nActual:\n{:?}\n\nExpected:\n{:?}",
            actual, expected
        );
    }

    /// Create a simple binary expression for testing
    pub fn and_expr(left: Expr, right: Expr) -> Expr {
        left.and(right)
    }

    /// Create a simple binary expression for testing
    pub fn or_expr(left: Expr, right: Expr) -> Expr {
        left.or(right)
    }

    /// Create an equality expression
    pub fn eq_expr(column: &str, value: impl Into<Expr>) -> Expr {
        col(column).eq(value.into())
    }

    /// Create a greater than expression
    pub fn gt_expr(column: &str, value: impl Into<Expr>) -> Expr {
        col(column).gt(value.into())
    }

    /// Create a less than expression
    pub fn lt_expr(column: &str, value: impl Into<Expr>) -> Expr {
        col(column).lt(value.into())
    }

    /// Build a filter plan
    pub fn build_filter(input: LogicalPlan, predicate: Expr) -> LogicalPlan {
        LogicalPlanBuilder::from(input)
            .filter(predicate)
            .unwrap()
            .build()
            .unwrap()
    }

    /// Build a projection plan
    pub fn build_project(input: LogicalPlan, exprs: Vec<Expr>) -> LogicalPlan {
        LogicalPlanBuilder::from(input)
            .project(exprs)
            .unwrap()
            .build()
            .unwrap()
    }
}

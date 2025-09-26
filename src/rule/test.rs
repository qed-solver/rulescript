#[cfg(test)]
pub mod utils {
    use datafusion::{
        arrow::datatypes::{DataType, Field, Schema as ArrowSchema},
        logical_expr::{LogicalPlan, LogicalPlanBuilder},
    };
    use std::sync::Arc;

    /// Create a minimal test table scan
    pub fn test_table() -> LogicalPlan {
        let schema = Arc::new(ArrowSchema::new(vec![
            Field::new("a", DataType::Int32, true),
            Field::new("b", DataType::Int32, true),
        ]));

        let table_source = Arc::new(datafusion::logical_expr::builder::LogicalTableSource::new(
            schema,
        ));

        LogicalPlanBuilder::scan("test", table_source, None)
            .unwrap()
            .build()
            .unwrap()
    }
}

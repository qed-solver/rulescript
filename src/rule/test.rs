#[cfg(test)]
pub mod utils {
    use crate::ast::{opaque::Type, scalar::Function};
    use datafusion::{
        arrow::datatypes::{DataType, Field, Schema as ArrowSchema},
        logical_expr::{Expr, LogicalPlan, LogicalPlanBuilder, col},
    };
    use std::sync::Arc;

    /// Create the standard Calcite test "dept" table
    /// Schema: deptno INTEGER, dname VARCHAR
    pub fn dept_table() -> LogicalPlan {
        let schema = Arc::new(ArrowSchema::new(vec![
            Field::new("deptno", DataType::Int32, false),
            Field::new("dname", DataType::Utf8, false),
        ]));

        let table_source = Arc::new(datafusion::logical_expr::builder::LogicalTableSource::new(
            schema,
        ));

        LogicalPlanBuilder::scan("dept", table_source, None)
            .unwrap()
            .build()
            .unwrap()
    }

    /// Create the standard Calcite test "emp" table  
    /// Schema: empno INTEGER, ename VARCHAR, deptno INTEGER, salary FLOAT
    pub fn emp_table() -> LogicalPlan {
        let schema = Arc::new(ArrowSchema::new(vec![
            Field::new("empno", DataType::Int32, false),
            Field::new("ename", DataType::Utf8, false),
            Field::new("deptno", DataType::Int32, true),
            Field::new("salary", DataType::Float64, true),
        ]));

        let table_source = Arc::new(datafusion::logical_expr::builder::LogicalTableSource::new(
            schema,
        ));

        LogicalPlanBuilder::scan("emp", table_source, None)
            .unwrap()
            .build()
            .unwrap()
    }

    /// Create a minimal test table (kept for backward compatibility)
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

    /// Create a test table with Binary columns for abstract function testing
    pub fn table_with_binary_columns(column_names: Vec<&str>) -> LogicalPlan {
        let fields: Vec<Field> = column_names
            .into_iter()
            .map(|name| Field::new(name, DataType::Binary, true))
            .collect();

        let schema = Arc::new(ArrowSchema::new(fields));
        let table_source = Arc::new(datafusion::logical_expr::builder::LogicalTableSource::new(
            schema,
        ));

        LogicalPlanBuilder::scan("test", table_source, None)
            .unwrap()
            .build()
            .unwrap()
    }

    /// Create an abstract function for testing
    pub fn test_function(name: &str, input_type: Type, output_type: Type) -> Function {
        Function::new(name.to_string(), vec![input_type], output_type)
    }

    /// Create a binary predicate function
    pub fn test_predicate(name: &str, input_type: Type) -> Function {
        Function::new(name.to_string(), vec![input_type], Type::Boolean)
    }

    /// Build a projection with an abstract function
    pub fn project_with_function(
        input: LogicalPlan,
        func: &Function,
        arg_col: &str,
        alias: &str,
    ) -> LogicalPlan {
        LogicalPlanBuilder::from(input)
            .project(vec![func.call(vec![col(arg_col)]).alias(alias)])
            .unwrap()
            .build()
            .unwrap()
    }

    /// Build a filter with an abstract predicate
    pub fn filter_with_predicate(
        input: LogicalPlan,
        pred: &Function,
        arg_expr: Expr,
    ) -> LogicalPlan {
        LogicalPlanBuilder::from(input)
            .filter(pred.call(vec![arg_expr]))
            .unwrap()
            .build()
            .unwrap()
    }
}

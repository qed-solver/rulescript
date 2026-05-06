pub mod utils {
    use crate::ast::{opaque::Type, pattern::ScalarPattern};
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

    /// Create a sales table for join testing
    /// Schema: sale_id INTEGER, product_id INTEGER, quantity INTEGER
    pub fn sales_table() -> LogicalPlan {
        let schema = Arc::new(ArrowSchema::new(vec![
            Field::new("sale_id", DataType::Int32, false),
            Field::new("product_id", DataType::Int32, false),
            Field::new("quantity", DataType::Int32, false),
        ]));

        let table_source = Arc::new(datafusion::logical_expr::builder::LogicalTableSource::new(
            schema,
        ));

        LogicalPlanBuilder::scan("sales", table_source, None)
            .unwrap()
            .build()
            .unwrap()
    }

    /// Create a product table for join testing
    /// Schema: product_id INTEGER, name VARCHAR, price FLOAT
    pub fn product_table() -> LogicalPlan {
        let schema = Arc::new(ArrowSchema::new(vec![
            Field::new("product_id", DataType::Int32, false),
            Field::new("name", DataType::Utf8, false),
            Field::new("price", DataType::Float64, false),
        ]));

        let table_source = Arc::new(datafusion::logical_expr::builder::LogicalTableSource::new(
            schema,
        ));

        LogicalPlanBuilder::scan("product", table_source, None)
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
    pub fn test_function(name: &str, input_type: Type, output_type: Type) -> ScalarPattern {
        ScalarPattern::new(name.to_string(), vec![input_type], output_type)
    }

    /// Create a binary predicate function
    pub fn test_predicate(name: &str, input_type: Type) -> ScalarPattern {
        ScalarPattern::new(name.to_string(), vec![input_type], Type::Boolean)
    }

    /// Build a projection with an abstract function
    pub fn project_with_function(
        input: LogicalPlan,
        func: &ScalarPattern,
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
        pred: &ScalarPattern,
        arg_expr: Expr,
    ) -> LogicalPlan {
        LogicalPlanBuilder::from(input)
            .filter(pred.call(vec![arg_expr]))
            .unwrap()
            .build()
            .unwrap()
    }

    /// Create an empty relation with the same schema as the given plan
    pub fn empty_from(plan: &LogicalPlan) -> LogicalPlan {
        use datafusion::logical_expr::EmptyRelation;
        LogicalPlan::EmptyRelation(EmptyRelation {
            produce_one_row: false,
            schema: plan.schema().clone(),
        })
    }
}

//! Table definitions for the interactive optimizer REPL

use datafusion::{
    arrow::datatypes::{DataType, Field, Schema},
    datasource::empty::EmptyTable,
    prelude::*,
};
use std::sync::Arc;

pub struct TableInfo {
    pub name: &'static str,
    pub description: &'static str,
    pub schema: Arc<Schema>,
}

impl TableInfo {
    pub fn display(&self) -> String {
        let fields: Vec<String> = self
            .schema
            .fields()
            .iter()
            .map(|f| {
                let nullable = if f.is_nullable() { " NULL" } else { "" };
                format!("{}: {:?}{}", f.name(), f.data_type(), nullable)
            })
            .collect();

        format!(
            "  • {} - {}\n    ({})",
            self.name,
            self.description,
            fields.join(", ")
        )
    }
}

pub fn create_emp_table() -> TableInfo {
    let schema = Arc::new(Schema::new(vec![
        Field::new("empno", DataType::Int32, false),
        Field::new("ename", DataType::Utf8, false),
        Field::new("job", DataType::Utf8, false),
        Field::new("mgr", DataType::Int32, true),
        Field::new("hiredate", DataType::Date32, false),
        Field::new("salary", DataType::Float64, false),
        Field::new("commission", DataType::Float64, true),
        Field::new("deptno", DataType::Int32, false),
    ]));

    TableInfo {
        name: "emp",
        description: "Employee table",
        schema,
    }
}

pub fn create_dept_table() -> TableInfo {
    let schema = Arc::new(Schema::new(vec![
        Field::new("deptno", DataType::Int32, false),
        Field::new("dname", DataType::Utf8, false),
        Field::new("loc", DataType::Utf8, true),
    ]));

    TableInfo {
        name: "dept",
        description: "Department table",
        schema,
    }
}

pub async fn register_tables(ctx: &SessionContext) -> Vec<TableInfo> {
    let tables = vec![create_emp_table(), create_dept_table()];

    for table in &tables {
        // Create an empty table with the schema
        let empty_table = EmptyTable::new(table.schema.clone());

        ctx.register_table(table.name, Arc::new(empty_table))
            .expect("Failed to register table");
    }

    tables
}

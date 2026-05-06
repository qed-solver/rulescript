use std::{cmp::Ordering, fmt};

use datafusion::{
    common::DFSchemaRef,
    error::{DataFusionError, Result},
    logical_expr::{Expr, LogicalPlan, UserDefinedLogicalNodeCore},
};

use crate::ast::opaque::Schema;

/// Source pattern that can be used as a variant in DataFusion's LogicalPlan
///
/// Represents an abstract table/source in pattern matching.
/// Can match any concrete plan with a compatible schema.
#[derive(Debug, Clone)]
pub struct Source {
    pub table_name: String,
    pub schema: Schema,
    // Cache the converted DataFusion schema
    df_schema: DFSchemaRef,
}

impl Source {
    pub fn new(table_name: String, schema: Schema) -> Self {
        let df_schema = schema.to_datafusion_schema();
        Self {
            table_name,
            schema,
            df_schema,
        }
    }
}

// Manually implement required traits for UserDefinedLogicalNodeCore
impl PartialEq for Source {
    fn eq(&self, other: &Self) -> bool {
        self.table_name == other.table_name && self.schema == other.schema
    }
}

impl Eq for Source {}

impl PartialOrd for Source {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for Source {
    fn cmp(&self, other: &Self) -> Ordering {
        match self.table_name.cmp(&other.table_name) {
            Ordering::Equal => self.schema.cmp(&other.schema),
            ord => ord,
        }
    }
}

impl std::hash::Hash for Source {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.table_name.hash(state);
        self.schema.hash(state);
        // Don't hash df_schema as it's derived from schema
    }
}

impl UserDefinedLogicalNodeCore for Source {
    fn name(&self) -> &str {
        "Source"
    }

    fn inputs(&self) -> Vec<&LogicalPlan> {
        // Source has no inputs
        Vec::new()
    }

    fn schema(&self) -> &DFSchemaRef {
        &self.df_schema
    }

    fn expressions(&self) -> Vec<Expr> {
        Vec::new()
    }

    fn fmt_for_explain(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(
            f,
            "Source: {} [fields: {}]",
            self.table_name,
            self.schema.field_count()
        )
    }

    fn with_exprs_and_inputs(&self, _exprs: Vec<Expr>, _inputs: Vec<LogicalPlan>) -> Result<Self> {
        // Source pattern should not be modified by optimizer
        Err(DataFusionError::Plan(
            "Source should not be modified by optimizer".to_string(),
        ))
    }
}

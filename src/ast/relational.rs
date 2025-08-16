use std::{cmp::Ordering, fmt, sync::Arc};

use datafusion::{
    common::DFSchemaRef,
    error::Result,
    logical_expr::{Extension, LogicalPlan, UserDefinedLogicalNodeCore},
    prelude::Expr,
};

use crate::ast::opaque::AbstractSchema;

// Source pattern that can be used as a variant in DataFusion's LogicalPlan
#[derive(Debug, Clone)]
pub struct SourcePattern {
    pub table_name: String,
    pub abstract_schema: AbstractSchema,
    // Cache the converted DataFusion schema
    df_schema: DFSchemaRef,
}

impl SourcePattern {
    pub fn new(table_name: String, abstract_schema: AbstractSchema) -> Self {
        let df_schema = abstract_schema.to_datafusion_schema();
        Self {
            table_name,
            abstract_schema,
            df_schema,
        }
    }
}

// Manually implement required traits for UserDefinedLogicalNodeCore
impl PartialEq for SourcePattern {
    fn eq(&self, other: &Self) -> bool {
        self.table_name == other.table_name && self.abstract_schema == other.abstract_schema
    }
}

impl Eq for SourcePattern {}

impl PartialOrd for SourcePattern {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for SourcePattern {
    fn cmp(&self, other: &Self) -> Ordering {
        match self.table_name.cmp(&other.table_name) {
            Ordering::Equal => self.abstract_schema.cmp(&other.abstract_schema),
            ord => ord,
        }
    }
}

impl std::hash::Hash for SourcePattern {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.table_name.hash(state);
        self.abstract_schema.hash(state);
        // Don't hash df_schema as it's derived from abstract_schema
    }
}

impl UserDefinedLogicalNodeCore for SourcePattern {
    fn name(&self) -> &str {
        "SourcePattern"
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
            "SourcePattern: {} [fields: {}]",
            self.table_name,
            self.abstract_schema.field_count()
        )
    }

    fn with_exprs_and_inputs(&self, _exprs: Vec<Expr>, _inputs: Vec<LogicalPlan>) -> Result<Self> {
        // Source pattern should not be modified by optimizer
        Err(datafusion::error::DataFusionError::Plan(
            "SourcePattern should not be modified by optimizer".to_string(),
        ))
    }
}

// A relational pattern is just a wrapper around a DataFusion LogicalPlan
#[derive(Debug, Clone)]
pub struct RelationalPattern {
    pub plan: LogicalPlan,
}

impl RelationalPattern {
    // Create a source pattern using our custom SourcePattern node
    pub fn source(table_name: String, abstract_schema: AbstractSchema) -> Self {
        let source_pattern = SourcePattern::new(table_name, abstract_schema);

        let plan = LogicalPlan::Extension(Extension {
            node: Arc::new(source_pattern),
        });

        Self { plan }
    }

    // Extract our custom SourcePattern if this pattern represents one
    pub fn as_source_pattern(&self) -> Option<&SourcePattern> {
        if let LogicalPlan::Extension(ext) = &self.plan {
            ext.node.as_any().downcast_ref::<SourcePattern>()
        } else {
            None
        }
    }
}

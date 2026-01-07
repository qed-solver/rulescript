use std::{cmp::Ordering, fmt};

use datafusion::{
    common::DFSchemaRef,
    error::{DataFusionError, Result},
    logical_expr::{Expr, LogicalPlan, UserDefinedLogicalNodeCore},
};

/// Empty pattern that can be used as a variant in DataFusion's LogicalPlan
///
/// Represents an abstract empty relation in pattern matching.
/// The schema is derived from the inner plan during instantiation.
#[derive(Debug, Clone)]
pub struct Empty {
    /// The inner plan whose schema this empty relation should match
    pub inner: LogicalPlan,
    // Cache the DataFusion schema (derived from inner)
    df_schema: DFSchemaRef,
}

impl Empty {
    pub fn new(inner: LogicalPlan) -> Self {
        let df_schema = inner.schema().clone();
        Self { inner, df_schema }
    }
}

// Manually implement required traits for UserDefinedLogicalNodeCore
impl PartialEq for Empty {
    fn eq(&self, other: &Self) -> bool {
        self.inner == other.inner
    }
}

impl Eq for Empty {}

impl PartialOrd for Empty {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for Empty {
    fn cmp(&self, other: &Self) -> Ordering {
        // LogicalPlan doesn't implement Ord, so we compare by debug string
        format!("{:?}", self.inner).cmp(&format!("{:?}", other.inner))
    }
}

impl std::hash::Hash for Empty {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        // Hash by debug string since LogicalPlan doesn't implement Hash
        format!("{:?}", self.inner).hash(state);
    }
}

impl UserDefinedLogicalNodeCore for Empty {
    fn name(&self) -> &str {
        "Empty"
    }

    fn inputs(&self) -> Vec<&LogicalPlan> {
        // Empty has no inputs
        vec![]
    }

    fn schema(&self) -> &DFSchemaRef {
        &self.df_schema
    }

    fn expressions(&self) -> Vec<Expr> {
        vec![]
    }

    fn fmt_for_explain(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "Empty")
    }

    fn with_exprs_and_inputs(&self, _exprs: Vec<Expr>, _inputs: Vec<LogicalPlan>) -> Result<Self> {
        // Empty should not be modified by optimizer
        Err(DataFusionError::Plan(
            "Empty should not be modified by optimizer".to_string(),
        ))
    }
}

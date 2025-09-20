mod default;

pub use default::DefaultMatcher;

use datafusion::{
    arrow::datatypes::DataType,
    error::DataFusionError,
    logical_expr::{Expr, LogicalPlan},
};
use thiserror::Error;

use crate::ast::relational::Rel;

/// Interface for pattern matching engines
pub trait PatternMatcher {
    /// Resolve pattern against concrete plan, extracting bindings
    fn resolve(&mut self, pattern: &Rel, concrete: &LogicalPlan) -> Result<(), RuleError>;

    /// Instantiate a template using internal bindings
    fn instantiate(&self, template: &Rel) -> Result<LogicalPlan, RuleError>;
}

/// Errors that can occur during pattern matching and rule application
#[derive(Debug, Error)]
pub enum RuleError {
    /// Pattern structure doesn't match the target
    #[error("Structure mismatch: pattern {pattern} does not match target {target}")]
    StructureMismatch {
        pattern: LogicalPlan,
        target: LogicalPlan,
    },

    /// Expression structure doesn't match
    #[error("Expression mismatch: pattern {pattern} does not match target {target}")]
    ExpressionMismatch { pattern: Expr, target: Expr },

    /// Partitioning failed - concrete item couldn't be matched to any abstract pattern
    #[error("Cannot match item at index {index} to any pattern")]
    NoMatchingPattern { index: usize },

    /// Inconsistent binding - same symbol bound to different values
    #[error("Inconsistent binding for '{symbol}'")]
    InconsistentBinding {
        symbol: String,
        #[source]
        details: BindingConflict,
    },

    /// Failed to instantiate template - missing binding
    #[error("Cannot instantiate: unbound symbol '{symbol}'")]
    InstantiationError { symbol: String },

    /// Referenced binding not found
    #[error("Unbound symbol: '{symbol}'")]
    UnboundSymbol { symbol: String },

    /// DataFusion error wrapper
    #[error("DataFusion error: {0}")]
    DataFusion(#[from] DataFusionError),
}

/// Details about why a binding conflict occurred
#[derive(Debug, Error)]
pub enum BindingConflict {
    #[error("Function already bound: previous={previous:?}, attempted={attempted:?}")]
    Expression { previous: Expr, attempted: Expr },

    #[error("Source already bound: previous={previous:?}, attempted={attempted:?}")]
    Plan {
        previous: LogicalPlan,
        attempted: LogicalPlan,
    },

    #[error("Type already bound: previous={previous:?}, attempted={attempted:?}")]
    Type {
        previous: DataType,
        attempted: DataType,
    },
}

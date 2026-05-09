mod default;

use std::any::Any;

pub use default::DefaultMatcher;

use datafusion::{
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
    fn instantiate(&mut self, template: &Rel) -> Result<LogicalPlan, RuleError>;

    /// Downcast to `&dyn Any` for accessing implementation-specific methods
    fn as_any(&self) -> &dyn Any;

    /// Downcast to `&mut dyn Any` for accessing implementation-specific methods
    fn as_any_mut(&mut self) -> &mut dyn Any;
}

/// Errors that can occur during pattern matching and rule application
#[derive(Debug, Error)]
pub enum RuleError {
    /// Pattern structure doesn't match the target
    #[error("Structure mismatch: pattern {pattern} does not match target {target}")]
    StructureMismatch {
        pattern: Box<LogicalPlan>,
        target: Box<LogicalPlan>,
    },

    /// Expression structure doesn't match
    #[error("Expression mismatch: pattern {pattern} does not match target {target}")]
    ExpressionMismatch {
        pattern: Box<Expr>,
        target: Box<Expr>,
    },

    /// Partitioning failed - concrete item couldn't be matched to any abstract pattern
    #[error("Cannot match item to any pattern: {item}")]
    NoMatchingPattern { item: String },

    /// Inconsistent binding - same symbol bound to different values
    #[error("Inconsistent binding for '{symbol}'")]
    InconsistentBinding {
        symbol: String,
        #[source]
        details: BindingConflict,
    },

    /// Referenced binding not found
    #[error("Unbound symbol: '{symbol}'")]
    UnboundSymbol { symbol: String },

    /// Invalid pattern structure
    #[error("Invalid pattern: {reason}")]
    InvalidPattern { reason: String },

    /// DataFusion error wrapper
    #[error("DataFusion error: {0}")]
    DataFusion(#[from] DataFusionError),
}

/// Details about why a binding conflict occurred
#[derive(Debug, Error)]
pub enum BindingConflict {
    #[error("Function already bound: previous={previous:?}, attempted={attempted:?}")]
    Expression {
        previous: Box<Expr>,
        attempted: Box<Expr>,
    },

    #[error("Source already bound: previous={previous:?}, attempted={attempted:?}")]
    Plan {
        previous: Box<LogicalPlan>,
        attempted: Box<LogicalPlan>,
    },
}

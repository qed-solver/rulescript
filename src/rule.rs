use std::collections::HashMap;

use datafusion::{
    arrow::datatypes::DataType,
    error::DataFusionError,
    logical_expr::{Expr, LogicalPlan},
};
use thiserror::Error;

use crate::Rel;

/// A rewrite rule that transforms one relational pattern to another
pub trait RewriteRule {
    /// The pattern to match
    fn from(&self) -> Rel;

    /// The pattern to produce
    fn to(&self) -> Rel;

    /// Optional rule name for debugging
    fn name(&self) -> &str {
        std::any::type_name::<Self>()
    }
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

    /// Inconsistent binding - same symbol bound to different values
    #[error("Inconsistent binding for '{symbol}'")]
    InconsistentBinding {
        symbol: String,
        #[source]
        details: BindingConflict,
    },

    /// Failed to instantiate template with bindings
    #[error("Failed to instantiate template: {reason}")]
    InstantiationError { reason: String },

    /// Referenced binding not found
    #[error("Unbound symbol: '{symbol}'")]
    UnboundSymbol { symbol: String },

    /// DataFusion error wrapper
    #[error("DataFusion error: {0}")]
    DataFusion(#[from] DataFusionError),

    /// Custom error for extensibility
    #[error("Custom error: {0}")]
    Custom(#[source] Box<dyn std::error::Error + Send + Sync>),
}

/// Details about what values conflicted in a binding attempt
#[derive(Debug, Error)]
pub enum BindingConflict {
    /// Conflicting expressions
    #[error(
        "Expression conflict - the same symbol was bound to different expressions:\n  Previous: {previous:?}\n  Attempted: {attempted:?}"
    )]
    Expression { previous: Expr, attempted: Expr },

    /// Conflicting plans
    #[error(
        "Plan conflict - the same source was bound to incompatible plans:\n  Previous: {previous}\n  Attempted: {attempted}"
    )]
    Plan {
        previous: LogicalPlan,
        attempted: LogicalPlan,
    },

    /// Conflicting types
    #[error(
        "Type conflict - the same type variable was bound to different types:\n  Previous: {previous:?}\n  Attempted: {attempted:?}"
    )]
    Type {
        previous: DataType,
        attempted: DataType,
    },
}

impl RuleError {
    /// Create a custom error
    pub fn custom<E>(error: E) -> Self
    where
        E: std::error::Error + Send + Sync + 'static,
    {
        RuleError::Custom(Box::new(error))
    }
}

/// Interface for pattern matching engines
pub trait PatternMatcher {
    /// Resolve pattern against concrete plan, extracting bindings
    fn resolve(&mut self, pattern: &Rel, concrete: &LogicalPlan) -> Result<(), RuleError>;

    /// Instantiate a template using internal bindings
    fn instantiate(&self, template: &Rel) -> Result<LogicalPlan, RuleError>;
}

/// Make rules applicable with a specific matcher
pub trait ApplicableRule<M: PatternMatcher + Default = DefaultMatcher>: RewriteRule {
    /// Try to apply this rule to a logical plan using the default matcher
    fn try_apply(&self, plan: &LogicalPlan) -> Result<LogicalPlan, RuleError> {
        let mut matcher = M::default();
        self.try_apply_with(plan, &mut matcher)
    }

    /// Try to apply this rule to a logical plan using a provided matcher
    fn try_apply_with(
        &self,
        plan: &LogicalPlan,
        matcher: &mut M,
    ) -> Result<LogicalPlan, RuleError> {
        // Resolve 'from' pattern against the plan
        let from = self.from();
        matcher.resolve(&from, plan)?;

        // Instantiate 'to' pattern with the internal bindings
        let to = self.to();
        matcher.instantiate(&to)
    }

    /// Check if this rule matches without transforming
    fn matches(&self, plan: &LogicalPlan) -> bool {
        let mut matcher = M::default();
        self.matches_with(plan, &mut matcher)
    }

    /// Check if this rule matches using a provided matcher
    fn matches_with(&self, plan: &LogicalPlan, matcher: &mut M) -> bool {
        let from = self.from();
        matcher.resolve(&from, plan).is_ok()
    }
}

/// Default pattern matcher that tracks bindings internally
#[derive(Debug, Default)]
pub struct DefaultMatcher {
    /// Abstract functions/predicates -> concrete expressions
    functions: HashMap<String, Expr>,

    /// Abstract sources -> concrete plans
    sources: HashMap<String, LogicalPlan>,

    /// Abstract types -> concrete DataFusion types
    types: HashMap<String, DataType>,
}

impl DefaultMatcher {
    pub fn new() -> Self {
        Self::default()
    }

    /// Bind a function to a concrete expression, checking for consistency
    pub fn bind_function(&mut self, name: String, expr: Expr) -> Result<(), RuleError> {
        if let Some(existing) = self.functions.get(&name) {
            if !self.exprs_equivalent(existing, &expr) {
                return Err(RuleError::InconsistentBinding {
                    symbol: name,
                    details: BindingConflict::Expression {
                        previous: existing.clone(),
                        attempted: expr,
                    },
                });
            }
        } else {
            self.functions.insert(name, expr);
        }
        Ok(())
    }

    /// Bind a source to a concrete plan, checking for consistency
    pub fn bind_source(&mut self, name: String, plan: LogicalPlan) -> Result<(), RuleError> {
        if let Some(existing) = self.sources.get(&name) {
            if !self.plans_compatible(existing, &plan) {
                return Err(RuleError::InconsistentBinding {
                    symbol: name,
                    details: BindingConflict::Plan {
                        previous: existing.clone(),
                        attempted: plan,
                    },
                });
            }
        } else {
            self.sources.insert(name, plan);
        }
        Ok(())
    }

    /// Bind a type to a concrete DataType, checking for consistency
    pub fn bind_type(&mut self, name: String, dtype: DataType) -> Result<(), RuleError> {
        if let Some(existing) = self.types.get(&name) {
            if !self.types_equivalent(existing, &dtype) {
                return Err(RuleError::InconsistentBinding {
                    symbol: name,
                    details: BindingConflict::Type {
                        previous: existing.clone(),
                        attempted: dtype,
                    },
                });
            }
        } else {
            self.types.insert(name, dtype);
        }
        Ok(())
    }

    /// Look up a bound function
    pub fn lookup_function(&self, name: &str) -> Option<&Expr> {
        self.functions.get(name)
    }

    /// Look up a bound source
    pub fn lookup_source(&self, name: &str) -> Option<&LogicalPlan> {
        self.sources.get(name)
    }

    /// Look up a bound type
    pub fn lookup_type(&self, name: &str) -> Option<&DataType> {
        self.types.get(name)
    }

    // Customizable equivalence checks - override in custom matchers

    /// Check if two expressions are equivalent (can be overridden)
    pub fn exprs_equivalent(&self, e1: &Expr, e2: &Expr) -> bool {
        e1 == e2
    }

    /// Check if two plans are compatible (can be overridden)
    pub fn plans_compatible(&self, p1: &LogicalPlan, p2: &LogicalPlan) -> bool {
        let fields1 = p1.schema().fields();
        let fields2 = p2.schema().fields();

        fields1.len() == fields2.len()
            && fields1
                .iter()
                .zip(fields2.iter())
                .all(|(f1, f2)| f1.data_type() == f2.data_type())
    }

    /// Check if two types are equivalent (can be overridden)
    pub fn types_equivalent(&self, t1: &DataType, t2: &DataType) -> bool {
        t1 == t2
    }
}

impl PatternMatcher for DefaultMatcher {
    fn resolve(&mut self, _pattern: &Rel, _concrete: &LogicalPlan) -> Result<(), RuleError> {
        // TODO: Implement actual resolution logic
        Err(RuleError::InstantiationError {
            reason: "DefaultMatcher not yet implemented".to_string(),
        })
    }

    fn instantiate(&self, _template: &Rel) -> Result<LogicalPlan, RuleError> {
        // TODO: Implement actual instantiation logic
        Err(RuleError::InstantiationError {
            reason: "DefaultMatcher not yet implemented".to_string(),
        })
    }
}

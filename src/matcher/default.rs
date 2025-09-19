use std::collections::HashMap;

use datafusion::{
    arrow::datatypes::DataType,
    logical_expr::{Expr, Extension, Filter, LogicalPlan, Projection, expr::ScalarFunction},
};

use crate::ast::{
    relational::{Rel, Source},
    scalar::Function,
};

use super::{BindingConflict, PatternMatcher, RuleError};

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
        if let Some(previous) = self.functions.insert(name.clone(), expr.clone()) {
            if previous != expr {
                // Restore the previous value and return error
                self.functions.insert(name.clone(), previous.clone());
                return Err(RuleError::InconsistentBinding {
                    symbol: name,
                    details: BindingConflict::Expression {
                        previous,
                        attempted: expr,
                    },
                });
            }
        }
        Ok(())
    }

    /// Bind a source to a concrete plan, checking for consistency
    pub fn bind_source(&mut self, name: String, plan: LogicalPlan) -> Result<(), RuleError> {
        if let Some(previous) = self.sources.insert(name.clone(), plan.clone()) {
            if previous != plan {
                // Restore the previous value and return error
                self.sources.insert(name.clone(), previous.clone());
                return Err(RuleError::InconsistentBinding {
                    symbol: name,
                    details: BindingConflict::Plan {
                        previous,
                        attempted: plan,
                    },
                });
            }
        }
        Ok(())
    }

    /// Bind a type to a concrete DataType, checking for consistency
    pub fn bind_type(&mut self, name: String, dtype: DataType) -> Result<(), RuleError> {
        if let Some(previous) = self.types.insert(name.clone(), dtype.clone()) {
            if previous != dtype {
                // Restore the previous value and return error
                self.types.insert(name.clone(), previous.clone());
                return Err(RuleError::InconsistentBinding {
                    symbol: name,
                    details: BindingConflict::Type {
                        previous,
                        attempted: dtype,
                    },
                });
            }
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

    // Helper methods for pattern matching

    /// Get a Source from an Extension node or return error
    fn as_source<'s>(
        &self,
        ext: &'s Extension,
        concrete: &LogicalPlan,
    ) -> Result<&'s Source, RuleError> {
        ext.node
            .as_any()
            .downcast_ref::<Source>()
            .ok_or_else(|| RuleError::StructureMismatch {
                pattern: LogicalPlan::Extension(ext.clone()),
                target: concrete.clone(),
            })
    }

    /// Check if a ScalarFunction is one of our abstract functions
    fn is_abstract_function(&self, func: &ScalarFunction) -> bool {
        // Check if the UDF's implementation is our Function type
        // We check by trying to get the inner implementation
        func.func.inner().as_any().is::<Function>()
    }

    // Specific resolvers for each plan type

    /// Resolve a Source pattern against any concrete plan
    fn resolve_source(
        &mut self,
        source: &Source,
        ext: &Extension,
        concrete: &LogicalPlan,
    ) -> Result<(), RuleError> {
        // TODO: Implement source resolution
        todo!("resolve_source")
    }

    /// Resolve a Filter pattern against a concrete Filter
    fn resolve_filter(&mut self, pattern: &Filter, concrete: &Filter) -> Result<(), RuleError> {
        // TODO: Implement filter resolution
        todo!("resolve_filter")
    }

    /// Resolve a Projection pattern against a concrete Projection
    fn resolve_projection(
        &mut self,
        pattern: &Projection,
        concrete: &Projection,
    ) -> Result<(), RuleError> {
        // TODO: Implement projection resolution
        todo!("resolve_projection")
    }

    /// Resolve pattern expressions against concrete expressions
    fn resolve_expr(&mut self, pattern: &Expr, concrete: &Expr) -> Result<(), RuleError> {
        // TODO: Implement expression resolution
        todo!("resolve_expr")
    }

    /// Resolve pattern plan against concrete plan
    fn resolve_plan(
        &mut self,
        pattern: &LogicalPlan,
        concrete: &LogicalPlan,
    ) -> Result<(), RuleError> {
        match (pattern, concrete) {
            // Source pattern can match any plan with compatible schema
            (LogicalPlan::Extension(ext), con_plan) => {
                let source = self.as_source(ext, con_plan)?;
                self.resolve_source(source, ext, concrete)
            }

            // Filter patterns match Filter nodes
            (LogicalPlan::Filter(pat_filter), LogicalPlan::Filter(con_filter)) => {
                self.resolve_filter(pat_filter, con_filter)
            }

            // Projection patterns match Projection nodes
            (LogicalPlan::Projection(pat_proj), LogicalPlan::Projection(con_proj)) => {
                self.resolve_projection(pat_proj, con_proj)
            }

            // TODO: Add other variants (Join, Union, Aggregate, etc.)

            // Structure mismatch
            _ => Err(RuleError::StructureMismatch {
                pattern: pattern.clone(),
                target: concrete.clone(),
            }),
        }
    }
}

impl PatternMatcher for DefaultMatcher {
    fn resolve(&mut self, pattern: &Rel, concrete: &LogicalPlan) -> Result<(), RuleError> {
        self.resolve_plan(&pattern.plan, concrete)
    }

    fn instantiate(&self, _template: &Rel) -> Result<LogicalPlan, RuleError> {
        // TODO: Implement actual instantiation logic
        Err(RuleError::InstantiationError {
            reason: "DefaultMatcher instantiate not yet implemented".to_string(),
        })
    }
}

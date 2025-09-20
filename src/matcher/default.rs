use std::collections::{HashMap, HashSet};

use datafusion::{
    common::Column,
    logical_expr::{
        BinaryExpr, Expr, Extension, Filter, LogicalPlan, Projection, expr::ScalarFunction,
    },
};

use crate::ast::relational::{Rel, Source};

use super::{BindingConflict, PatternMatcher, RuleError};

/// Default pattern matcher that tracks bindings internally
#[derive(Debug, Default)]
pub struct DefaultMatcher {
    /// Abstract field name -> Set of original column names (partition)
    field_partitions: HashMap<String, HashSet<String>>,

    /// Abstract functions/predicates -> concrete expressions
    functions: HashMap<String, Expr>,

    /// Abstract sources -> concrete plans (original)
    sources: HashMap<String, LogicalPlan>,
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

    /// Look up a bound function
    pub fn lookup_function(&self, name: &str) -> Option<&Expr> {
        self.functions.get(name)
    }

    /// Look up a bound source
    pub fn lookup_source(&self, name: &str) -> Option<&LogicalPlan> {
        self.sources.get(name)
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

    /// Generic partitioning routine: assign each concrete item to first matching abstract pattern
    fn partition_items<C, A>(
        &mut self,
        concrete_items: impl IntoIterator<Item = C>,
        abstract_patterns: &[A],
        mut try_match: impl FnMut(&mut Self, &C, &A) -> Result<(), RuleError>,
    ) -> Result<(), RuleError> {
        for (concrete_idx, concrete_item) in concrete_items.into_iter().enumerate() {
            let mut matched = false;

            for abstract_pattern in abstract_patterns {
                // Try matching - Ok means success, Err means try next pattern
                if try_match(self, &concrete_item, abstract_pattern).is_ok() {
                    matched = true;
                    break;
                }
                // Any error just means "try next pattern"
            }

            if !matched {
                return Err(RuleError::NoMatchingPattern {
                    index: concrete_idx,
                });
            }
        }

        Ok(())
    }

    // Specific resolvers for each plan type

    /// Resolve an abstract function against a concrete expression
    fn resolve_abstract_function(
        &mut self,
        pat_func: &ScalarFunction,
        concrete: &Expr,
    ) -> Result<(), RuleError> {
        // TODO: Bind the abstract function to the concrete expression
        todo!("resolve_abstract_function")
    }

    /// Resolve binary expressions
    fn resolve_binary_expr(
        &mut self,
        pat_binary: &BinaryExpr,
        con_binary: &BinaryExpr,
    ) -> Result<(), RuleError> {
        // TODO: Match operator and recursively match operands
        todo!("resolve_binary_expr")
    }

    /// Resolve column references
    fn resolve_column(&mut self, pat_col: &Column, con_col: &Column) -> Result<(), RuleError> {
        // TODO: Check if concrete column is in the partition for the abstract field
        todo!("resolve_column")
    }

    /// Resolve pattern expressions against concrete expressions
    fn resolve_expr(&mut self, pattern: &Expr, concrete: &Expr) -> Result<(), RuleError> {
        match (pattern, concrete) {
            // Abstract function - binds to entire concrete expression
            (Expr::ScalarFunction(pat_func), con_expr) => {
                self.resolve_abstract_function(pat_func, con_expr)
            }

            // Binary expressions - match operator and operands
            (Expr::BinaryExpr(pat_binary), Expr::BinaryExpr(con_binary)) => {
                self.resolve_binary_expr(pat_binary, con_binary)
            }

            // Column references - check against field partitions
            (Expr::Column(pat_col), Expr::Column(con_col)) => self.resolve_column(pat_col, con_col),

            // Structure mismatch
            _ => Err(RuleError::ExpressionMismatch {
                pattern: pattern.clone(),
                target: concrete.clone(),
            }),
        }
    }

    /// Resolve a Source pattern against any concrete plan
    fn resolve_source(&mut self, source: &Source, concrete: &LogicalPlan) -> Result<(), RuleError> {
        // Store original concrete plan for instantiation later
        self.bind_source(source.table_name.clone(), concrete.clone())?;

        // Work directly with the concrete schema - no annotation
        let concrete_schema = concrete.schema();
        let concrete_fields = concrete_schema.fields().to_vec();

        // Use partition_items with field information directly
        self.partition_items(
            concrete_fields,
            &source.schema.fields,
            |matcher, concrete_field, abstract_field| {
                // Check nullable property
                if abstract_field.nullable != concrete_field.is_nullable() {
                    return Err(RuleError::StructureMismatch {
                        pattern: LogicalPlan::Extension(Extension {
                            node: std::sync::Arc::new(source.clone()),
                        }),
                        target: concrete.clone(),
                    });
                }

                // Add the original column name to the partition for this abstract field
                matcher
                    .field_partitions
                    .entry(abstract_field.name.clone())
                    .or_default()
                    .insert(concrete_field.name().to_string());
                Ok(())
            },
        )?;

        Ok(())
    }

    /// Resolve a Filter pattern against a concrete Filter
    fn resolve_filter(&mut self, pattern: &Filter, concrete: &Filter) -> Result<(), RuleError> {
        // First, recursively match the inputs
        self.resolve_plan(&pattern.input, &concrete.input)?;

        // Match the filter predicate expression
        self.resolve_expr(&pattern.predicate, &concrete.predicate)
    }

    /// Resolve a Projection pattern against a concrete Projection
    fn resolve_projection(
        &mut self,
        pattern: &Projection,
        concrete: &Projection,
    ) -> Result<(), RuleError> {
        // First, recursively match the inputs
        self.resolve_plan(&pattern.input, &concrete.input)?;

        // Use the generic partitioning routine
        self.partition_items(
            concrete.expr.clone(),
            &pattern.expr,
            |matcher, con_expr, pat_expr| matcher.resolve_expr(pat_expr, con_expr),
        )
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
                let pat_source = self.as_source(ext, con_plan)?;
                self.resolve_source(pat_source, con_plan)
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
        todo!("instantiate not yet implemented")
    }
}

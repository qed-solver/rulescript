use std::{
    collections::{HashMap, HashSet, hash_map::Entry},
    fmt::Debug,
    sync::Arc,
};

use datafusion::{
    common::Column,
    logical_expr::{
        BinaryExpr, Expr, Extension, Filter, LogicalPlan, Operator, Projection,
        expr::ScalarFunction,
    },
};

use crate::ast::{
    relational::{Rel, Source},
    scalar::Function,
};

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
    /// Create a new empty matcher
    pub fn new() -> Self {
        Self::default()
    }

    /// Bind a function to a concrete expression, checking for consistency
    pub fn bind_function(&mut self, name: String, expr: Expr) -> Result<(), RuleError> {
        match self.functions.entry(name.clone()) {
            Entry::Occupied(entry) => {
                if entry.get() != &expr {
                    return Err(RuleError::InconsistentBinding {
                        symbol: name,
                        details: BindingConflict::Expression {
                            previous: Box::new(entry.get().clone()),
                            attempted: Box::new(expr),
                        },
                    });
                }
            }
            Entry::Vacant(entry) => {
                entry.insert(expr);
            }
        }
        Ok(())
    }

    /// Bind a source to a concrete plan, checking for consistency
    pub fn bind_source(&mut self, name: String, plan: LogicalPlan) -> Result<(), RuleError> {
        match self.sources.entry(name.clone()) {
            Entry::Occupied(entry) => {
                if entry.get() != &plan {
                    return Err(RuleError::InconsistentBinding {
                        symbol: name,
                        details: BindingConflict::Plan {
                            previous: Box::new(entry.get().clone()),
                            attempted: Box::new(plan),
                        },
                    });
                }
            }
            Entry::Vacant(entry) => {
                entry.insert(plan);
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

    // ===== HELPER METHODS =====

    /// Extract a Source from an Extension node or return error
    fn as_source<'s>(
        ext: &'s Extension,
        pattern: &LogicalPlan,
        concrete: &LogicalPlan,
    ) -> Result<&'s Source, RuleError> {
        ext.node
            .as_any()
            .downcast_ref::<Source>()
            .ok_or_else(|| RuleError::StructureMismatch {
                pattern: Box::new(pattern.clone()),
                target: Box::new(concrete.clone()),
            })
    }

    /// Extract an abstract Function from a ScalarFunction or return error
    fn as_abstract_function<'s>(
        func: &'s ScalarFunction,
        pattern: &Expr,
        concrete: &Expr,
    ) -> Result<&'s Function, RuleError> {
        func.func
            .inner()
            .as_any()
            .downcast_ref::<Function>()
            .ok_or_else(|| RuleError::ExpressionMismatch {
                pattern: Box::new(pattern.clone()),
                target: Box::new(concrete.clone()),
            })
    }

    /// Generic partitioning routine: assign each item to first matching pattern
    fn partition<P, I: Debug>(
        &mut self,
        patterns: impl IntoIterator<Item = P>,
        items: impl IntoIterator<Item = I>,
        mut try_match: impl FnMut(&mut Self, &P, &I) -> Result<(), RuleError>,
    ) -> Result<(), RuleError> {
        let patterns = patterns.into_iter().collect::<Vec<_>>();

        for item in items {
            let mut matched = false;

            for pattern in &patterns {
                // Try matching - Ok means success, Err means try next pattern
                if try_match(self, pattern, &item).is_ok() {
                    matched = true;
                    break;
                }
            }

            if !matched {
                return Err(RuleError::NoMatchingPattern {
                    item: format!("{:?}", item),
                });
            }
        }

        Ok(())
    }

    /// Flatten binary expressions with the same operator into a list (DFS order)
    fn flatten_binary_op<'e>(expr: &'e Expr, op: &Operator) -> Vec<&'e Expr> {
        match expr {
            Expr::BinaryExpr(binary) if binary.op == *op => {
                // Same operator - recursively flatten both sides
                let mut terms = Self::flatten_binary_op(&binary.left, op);
                terms.extend(Self::flatten_binary_op(&binary.right, op));
                terms
            }
            other => {
                // Different operator or not binary - return single term
                vec![other]
            }
        }
    }

    // ===== RESOLUTION METHODS =====

    /// Main dispatcher for resolving pattern plans against concrete plans
    fn resolve_plan(
        &mut self,
        pattern: &LogicalPlan,
        concrete: &LogicalPlan,
    ) -> Result<(), RuleError> {
        match (pattern, concrete) {
            // Source pattern can match any plan with compatible schema
            (LogicalPlan::Extension(ext), con_plan) => {
                let pat_source = Self::as_source(ext, pattern, concrete)?;
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

            // TODO: Add other plan types (Join, Union, Aggregate, etc.) as needed

            // Structure mismatch
            _ => Err(RuleError::StructureMismatch {
                pattern: Box::new(pattern.clone()),
                target: Box::new(concrete.clone()),
            }),
        }
    }

    /// Resolve a Source pattern against any concrete plan
    fn resolve_source(&mut self, source: &Source, concrete: &LogicalPlan) -> Result<(), RuleError> {
        // Store original concrete plan for instantiation later (needs to be cloned for storage)
        self.bind_source(source.table_name.clone(), concrete.clone())?;

        // Work directly with the concrete schema - no annotation
        let concrete_schema = concrete.schema();
        let concrete_fields = concrete_schema.fields();

        // Use partition with field information directly
        self.partition(
            &source.schema.fields,
            concrete_fields,
            |matcher, abstract_field, concrete_field| {
                // Check nullable property
                if abstract_field.nullable != concrete_field.is_nullable() {
                    return Err(RuleError::StructureMismatch {
                        pattern: Box::new(LogicalPlan::Extension(Extension {
                            node: Arc::new(source.clone()),
                        })),
                        target: Box::new(concrete.clone()),
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
        self.partition(
            &pattern.expr,
            &concrete.expr,
            |matcher, pat_expr, con_expr| matcher.resolve_expr(pat_expr, con_expr),
        )
    }

    /// Main dispatcher for resolving expressions
    fn resolve_expr(&mut self, pattern: &Expr, concrete: &Expr) -> Result<(), RuleError> {
        match (pattern, concrete) {
            // Abstract function can match any expression
            (Expr::ScalarFunction(pat_func), _)
                if pat_func
                    .func
                    .inner()
                    .as_any()
                    .downcast_ref::<Function>()
                    .is_some() =>
            {
                self.resolve_abstract_function(pat_func, concrete)
            }

            // Binary expressions
            (Expr::BinaryExpr(pat_binary), Expr::BinaryExpr(con_binary)) => {
                self.resolve_binary_expr(pat_binary, con_binary)
            }

            // Columns must match exactly
            (Expr::Column(pat_col), Expr::Column(con_col)) => self.resolve_column(pat_col, con_col),

            // TODO: Handle other expression types as needed
            _ => Err(RuleError::ExpressionMismatch {
                pattern: Box::new(pattern.clone()),
                target: Box::new(concrete.clone()),
            }),
        }
    }

    /// Resolve an abstract function against a concrete expression
    fn resolve_abstract_function(
        &mut self,
        pat_func: &ScalarFunction,
        concrete: &Expr,
    ) -> Result<(), RuleError> {
        // Try to get our abstract Function from the ScalarFunction
        let pattern_expr = Expr::ScalarFunction(pat_func.clone());
        let abstract_func = Self::as_abstract_function(pat_func, &pattern_expr, concrete)?;

        // Get pattern's column references directly from the function args (assumes well-formed pattern)
        let pattern_columns = pattern_expr.column_refs();

        // Get concrete expression's column references
        let concrete_columns = concrete.column_refs();

        // Use partition to ensure each concrete column matches some pattern column
        // This enforces that concrete expression only uses columns from allowed partitions
        self.partition(
            &pattern_columns,
            concrete_columns.iter(),
            |matcher, pat_col, con_col| matcher.resolve_column(pat_col, con_col),
        )?;

        // Bind this abstract function to the entire concrete expression
        self.bind_function(abstract_func.name.clone(), concrete.clone())?;

        Ok(())
    }

    /// Resolve binary expressions
    fn resolve_binary_expr(
        &mut self,
        pat_binary: &BinaryExpr,
        con_binary: &BinaryExpr,
    ) -> Result<(), RuleError> {
        // Check if operators match
        if pat_binary.op != con_binary.op {
            return Err(RuleError::ExpressionMismatch {
                pattern: Box::new(Expr::BinaryExpr(pat_binary.clone())),
                target: Box::new(Expr::BinaryExpr(con_binary.clone())),
            });
        }

        match pat_binary.op {
            Operator::And | Operator::Or => {
                // For commutative AND/OR, flatten and use partition_items
                let pattern_expr = Expr::BinaryExpr(pat_binary.clone());
                let pattern_terms = Self::flatten_binary_op(&pattern_expr, &pat_binary.op);

                let concrete_expr = Expr::BinaryExpr(con_binary.clone());
                let concrete_terms = Self::flatten_binary_op(&concrete_expr, &con_binary.op);

                // Use partition to match concrete terms to pattern terms
                self.partition(
                    &pattern_terms,
                    concrete_terms,
                    |matcher, pat_term, con_term| matcher.resolve_expr(pat_term, con_term),
                )
            }
            _ => {
                // For non-commutative operators, match structurally
                self.resolve_expr(&pat_binary.left, &con_binary.left)?;
                self.resolve_expr(&pat_binary.right, &con_binary.right)
            }
        }
    }

    /// Resolve a column reference
    fn resolve_column(&mut self, pat_col: &Column, con_col: &Column) -> Result<(), RuleError> {
        // Check if concrete column belongs to the partition for the pattern column
        let partition = self.field_partitions.get(&pat_col.name);

        // If we have a partition for this abstract field, check if concrete column is in it
        if let Some(allowed_columns) = partition {
            if !allowed_columns.contains(&con_col.name) {
                return Err(RuleError::ExpressionMismatch {
                    pattern: Box::new(Expr::Column(pat_col.clone())),
                    target: Box::new(Expr::Column(con_col.clone())),
                });
            }
        }
        // If no partition exists yet, this might be ok (depends on context)
        // The partition would have been created during Source matching

        Ok(())
    }

    // ===== INSTANTIATION METHODS =====

    /// Main dispatcher for instantiating plans
    fn instantiate_plan(&self, template: &LogicalPlan) -> Result<LogicalPlan, RuleError> {
        match template {
            // Source pattern: look up the bound concrete plan
            LogicalPlan::Extension(ext) => {
                if let Some(source) = ext.node.as_any().downcast_ref::<Source>() {
                    self.instantiate_source(source)
                } else {
                    // Not a Source node, pass through unchanged
                    Ok(template.clone())
                }
            }

            // Filter: instantiate input and predicate
            LogicalPlan::Filter(filter) => self.instantiate_filter(filter),

            // Projection: instantiate input and expressions
            LogicalPlan::Projection(projection) => self.instantiate_projection(projection),

            // TODO: Add other plan types as needed

            // Other plan types: pass through unchanged (shouldn't appear in templates)
            other => Ok(other.clone()),
        }
    }

    /// Instantiate a Source pattern by looking up the bound plan
    fn instantiate_source(&self, _source: &Source) -> Result<LogicalPlan, RuleError> {
        // TODO: Look up source.table_name in self.sources and return bound plan
        todo!("instantiate_source")
    }

    /// Instantiate a Filter node by transforming input and predicate
    fn instantiate_filter(&self, _filter: &Filter) -> Result<LogicalPlan, RuleError> {
        // TODO: Recursively instantiate input plan and predicate expression
        todo!("instantiate_filter")
    }

    /// Instantiate a Projection node by transforming input and expressions
    fn instantiate_projection(&self, _projection: &Projection) -> Result<LogicalPlan, RuleError> {
        // TODO: Recursively instantiate input plan and projection expressions
        todo!("instantiate_projection")
    }

    /// Main dispatcher for instantiating expressions
    fn instantiate_expr(&self, template: &Expr) -> Result<Expr, RuleError> {
        match template {
            // Abstract function: look up in bindings
            Expr::ScalarFunction(func) => {
                // Check if this is an abstract function
                if func
                    .func
                    .inner()
                    .as_any()
                    .downcast_ref::<Function>()
                    .is_some()
                {
                    self.instantiate_abstract_function(func)
                } else {
                    // Regular function, pass through unchanged
                    Ok(template.clone())
                }
            }

            // Binary expression: recursively instantiate operands
            Expr::BinaryExpr(binary) => self.instantiate_binary_expr(binary),

            // Column: keep as-is (already concrete)
            Expr::Column(column) => self.instantiate_column(column),

            // Literal: keep as-is
            Expr::Literal(..) => Ok(template.clone()),

            // TODO: Handle other expression types as needed
            // For now, pass through unchanged
            _ => Ok(template.clone()),
        }
    }

    /// Instantiate an abstract function by looking up its binding
    fn instantiate_abstract_function(&self, _func: &ScalarFunction) -> Result<Expr, RuleError> {
        // TODO: Extract Function and look up in self.functions
        todo!("instantiate_abstract_function")
    }

    /// Instantiate a binary expression by recursively transforming operands
    fn instantiate_binary_expr(&self, _binary: &BinaryExpr) -> Result<Expr, RuleError> {
        // TODO: Recursively instantiate left and right operands
        todo!("instantiate_binary_expr")
    }

    /// Instantiate a column (pass through as columns are already concrete)
    fn instantiate_column(&self, column: &Column) -> Result<Expr, RuleError> {
        // Columns in templates are already concrete references
        Ok(Expr::Column(column.clone()))
    }
}

impl PatternMatcher for DefaultMatcher {
    fn resolve(&mut self, pattern: &Rel, concrete: &LogicalPlan) -> Result<(), RuleError> {
        self.resolve_plan(&pattern.plan, concrete)
    }

    fn instantiate(&self, template: &Rel) -> Result<LogicalPlan, RuleError> {
        self.instantiate_plan(&template.plan)
    }
}

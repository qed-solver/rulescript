use std::{collections::HashMap, fmt::Debug, sync::Arc};

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
    /// Abstract field name -> Ordered list of concrete Column objects
    fields: HashMap<String, Vec<Column>>,

    /// Abstract functions/predicates -> List of concrete expressions (usually single)
    functions: HashMap<String, Vec<Expr>>,

    /// Abstract sources -> concrete plans (original)
    sources: HashMap<String, LogicalPlan>,

    /// Track the qualified name order of the top-level output columns
    /// Used to preserve column ordering during projection instantiation
    output_schema: Vec<String>,
}

impl DefaultMatcher {
    /// Create a new empty matcher
    pub fn new() -> Self {
        Self::default()
    }

    // ===== HELPER METHODS =====

    /// Extract a Source from an Extension node
    fn as_source(ext: &Extension) -> Option<&Source> {
        ext.node.as_any().downcast_ref::<Source>()
    }

    /// Extract an abstract Function from a ScalarFunction
    fn as_abstract_function(func: &ScalarFunction) -> Option<&Function> {
        func.func.inner().as_any().downcast_ref::<Function>()
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
                let pat_source =
                    Self::as_source(ext).ok_or_else(|| RuleError::StructureMismatch {
                        pattern: Box::new(pattern.clone()),
                        target: Box::new(concrete.clone()),
                    })?;
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
        // Check for consistency if already bound
        if let Some(existing) = self.sources.get(&source.table_name) {
            if existing != concrete {
                return Err(RuleError::InconsistentBinding {
                    symbol: source.table_name.clone(),
                    details: BindingConflict::Plan {
                        previous: Box::new(existing.clone()),
                        attempted: Box::new(concrete.clone()),
                    },
                });
            }
        } else {
            self.sources
                .insert(source.table_name.clone(), concrete.clone());
        }

        // Use columns() which provides proper qualified Column objects
        // paired with their corresponding fields
        let concrete_schema = concrete
            .schema()
            .columns()
            .into_iter()
            .zip(concrete.schema().fields().iter());

        self.partition(
            &source.schema.fields,
            concrete_schema,
            |matcher, abstract_field, (concrete_column, concrete_field)| {
                // Check nullable property:
                // - If pattern field is non-nullable, concrete must also be non-nullable
                // - If pattern field is nullable, concrete can be either nullable or non-nullable
                if !abstract_field.nullable && concrete_field.is_nullable() {
                    return Err(RuleError::StructureMismatch {
                        pattern: Box::new(LogicalPlan::Extension(Extension {
                            node: Arc::new(source.clone()),
                        })),
                        target: Box::new(concrete.clone()),
                    });
                }

                // Now concrete_column is already a Column with proper qualifiers
                matcher
                    .fields
                    .entry(abstract_field.name.clone())
                    .or_default()
                    .push(concrete_column.clone());
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

        // Check if all pattern expressions are column references
        let pattern_cols = pattern
            .expr
            .iter()
            .map(|e| {
                if let Expr::Column(col) = e {
                    Ok(col)
                } else {
                    Err(())
                }
            })
            .collect::<Result<Vec<_>, _>>();

        // If pattern has all columns, check for partition-based matching
        if let Ok(pattern_cols) = pattern_cols {
            // Pattern has all columns, so concrete must also have all columns
            let concrete_cols = concrete
                .expr
                .iter()
                .map(|e| {
                    if let Expr::Column(col) = e {
                        Ok(col)
                    } else {
                        // Pattern expects columns but concrete has non-column expression
                        Err(RuleError::NoMatchingPattern {
                            item: e.to_string(),
                        })
                    }
                })
                .collect::<Result<Vec<_>, _>>()?;

            // Collect all columns from pattern's partitions in order
            let mut expected_columns = Vec::new();
            for pat_col in pattern_cols {
                if let Some(partition) = self.fields.get(&pat_col.name) {
                    expected_columns.extend(partition.clone());
                } else {
                    // Pattern column not bound yet, can't match
                    return Err(RuleError::UnboundSymbol {
                        symbol: pat_col.name.clone(),
                    });
                }
            }

            // Check if the concatenated partitions match the concrete columns exactly
            let concrete_columns = concrete_cols.iter().map(|&c| c.clone()).collect::<Vec<_>>();
            if expected_columns == concrete_columns {
                // Matches! The concrete columns are exactly the concatenation of pattern partitions
                return Ok(());
            } else {
                // Doesn't match - concrete columns don't match the expected partition concatenation
                return Err(RuleError::StructureMismatch {
                    pattern: Box::new(LogicalPlan::Projection(pattern.clone())),
                    target: Box::new(LogicalPlan::Projection(concrete.clone())),
                });
            }
        }

        // Otherwise use the generic partitioning routine
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
            (Expr::ScalarFunction(pat_func), con_expr) => {
                self.resolve_abstract_function(pat_func, con_expr)
            }

            // Binary expressions
            (Expr::BinaryExpr(pat_binary), Expr::BinaryExpr(con_binary)) => {
                self.resolve_binary_expr(pat_binary, con_binary)
            }

            // Columns must match exactly
            (Expr::Column(pat_col), Expr::Column(con_col)) => self.resolve_column(pat_col, con_col),

            // Alias in pattern - resolve inner pattern against concrete (which may or may not be aliased)
            (Expr::Alias(pat_alias), concrete_expr) => {
                self.resolve_expr(&pat_alias.expr, concrete_expr)
            }

            // Alias in concrete but not in pattern - try to match pattern against inner expression
            (pattern_expr, Expr::Alias(con_alias)) => {
                self.resolve_expr(pattern_expr, &con_alias.expr)
            }

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
        let abstract_func =
            Self::as_abstract_function(pat_func).ok_or_else(|| RuleError::ExpressionMismatch {
                pattern: Box::new(pattern_expr.clone()),
                target: Box::new(concrete.clone()),
            })?;

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
        // Add to the list of expressions bound to this function (usually just one)
        self.functions
            .entry(abstract_func.name.clone())
            .or_default()
            .push(concrete.clone());

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
                )?;

                // After partition, consolidate multiple bindings for abstract functions
                // that appeared in this flattened expression
                for pat_term in &pattern_terms {
                    let Expr::ScalarFunction(func) = pat_term else {
                        continue;
                    };
                    let Some(abstract_func) = Self::as_abstract_function(func) else {
                        continue;
                    };
                    let Some(exprs) = self.functions.remove(&abstract_func.name) else {
                        continue;
                    };

                    // Consolidate multiple expressions into single conjunction/disjunction
                    let combined = exprs
                        .into_iter()
                        .reduce(|acc, expr| {
                            Expr::BinaryExpr(BinaryExpr::new(
                                Box::new(acc),
                                pat_binary.op,
                                Box::new(expr),
                            ))
                        })
                        .unwrap_or_default();
                    self.functions
                        .insert(abstract_func.name.clone(), vec![combined]);
                }

                Ok(())
            }
            _ => {
                // For non-commutative operators, match structurally
                self.resolve_expr(&pat_binary.left, &con_binary.left)?;
                self.resolve_expr(&pat_binary.right, &con_binary.right)
            }
        }
    }

    /// Resolve a column reference (for non-projection contexts like filters)
    fn resolve_column(&mut self, pat_col: &Column, con_col: &Column) -> Result<(), RuleError> {
        // Get the list of allowed columns for this abstract field
        let allowed_columns =
            self.fields
                .get(&pat_col.name)
                .ok_or_else(|| RuleError::UnboundSymbol {
                    symbol: pat_col.name.clone(),
                })?;

        // Check if concrete column is in the allowed list
        if !allowed_columns.contains(con_col) {
            return Err(RuleError::ExpressionMismatch {
                pattern: Box::new(Expr::Column(pat_col.clone())),
                target: Box::new(Expr::Column(con_col.clone())),
            });
        }

        Ok(())
    }

    // ===== INSTANTIATION METHODS =====

    /// Main dispatcher for instantiating plans
    fn instantiate_plan(&self, template: &LogicalPlan) -> Result<LogicalPlan, RuleError> {
        match template {
            // Source pattern: look up the bound concrete plan
            LogicalPlan::Extension(ext) => {
                let source = Self::as_source(ext).ok_or_else(|| RuleError::InvalidPattern {
                    reason: format!(
                        "Unexpected extension node in template: {:?}",
                        ext.node.name()
                    ),
                })?;
                self.instantiate_source(source)
            }

            // Filter: instantiate input and predicate
            LogicalPlan::Filter(filter) => self.instantiate_filter(filter),

            // Projection: instantiate input and expressions
            LogicalPlan::Projection(projection) => self.instantiate_projection(projection),

            // TODO: Add other plan types as needed

            // Other plan types should not appear in templates
            other => Err(RuleError::InvalidPattern {
                reason: format!("Unsupported plan type in template: {:?}", other),
            }),
        }
    }

    /// Instantiate a Source pattern by looking up the bound plan
    fn instantiate_source(&self, source: &Source) -> Result<LogicalPlan, RuleError> {
        // Look up the source by table name in our bindings
        self.sources
            .get(&source.table_name)
            .cloned()
            .ok_or_else(|| RuleError::UnboundSymbol {
                symbol: source.table_name.clone(),
            })
    }

    /// Instantiate a Filter node by transforming input and predicate
    fn instantiate_filter(&self, filter: &Filter) -> Result<LogicalPlan, RuleError> {
        // First, recursively instantiate the input
        let new_input = Arc::new(self.instantiate_plan(&filter.input)?);

        // Instantiate the filter predicate expression
        // For filters, we expect exactly one expression back
        let new_predicate = self
            .instantiate_expr(&filter.predicate)?
            .pop()
            .ok_or_else(|| RuleError::InvalidPattern {
                reason: "Filter predicate must resolve to a single expression".to_string(),
            })?;

        // Build new Filter node with instantiated components
        Ok(LogicalPlan::Filter(Filter::try_new(
            new_predicate,
            new_input,
        )?))
    }

    /// Instantiate a Projection node by transforming input and expressions
    fn instantiate_projection(&self, projection: &Projection) -> Result<LogicalPlan, RuleError> {
        // First, recursively instantiate the input
        let new_input = Arc::new(self.instantiate_plan(&projection.input)?);

        // Instantiate all projection expressions, flattening results
        let mut all_exprs = Vec::new();
        for template_expr in &projection.expr {
            all_exprs.extend(self.instantiate_expr(template_expr)?);
        }

        // Sort expressions by the original output column order
        all_exprs.sort_by_key(|expr| {
            // Get the qualified name of the expression
            let (_table_ref, name) = expr.qualified_name();

            // Find its position in the original order
            self.output_schema
                .iter()
                .position(|output_name| output_name == &name)
        });

        // Build new Projection node with instantiated and sorted components
        Ok(LogicalPlan::Projection(Projection::try_new(
            all_exprs, new_input,
        )?))
    }

    /// Main dispatcher for instantiating expressions
    /// Returns a Vec because abstract functions can expand to multiple expressions
    fn instantiate_expr(&self, template: &Expr) -> Result<Vec<Expr>, RuleError> {
        match template {
            // Abstract function: look up in bindings and return all bound expressions
            Expr::ScalarFunction(func) => self.instantiate_abstract_function(func),

            // Binary expression: recursively instantiate operands (but stay as single expr)
            Expr::BinaryExpr(binary) => self.instantiate_binary_expr(binary),

            // Column: expand to all concrete columns in the partition
            Expr::Column(column) => self.instantiate_column(column),

            // TODO: Handle other expression types as needed
            // Error on unexpected patterns instead of passing through
            other => Err(RuleError::InvalidPattern {
                reason: format!("Unsupported expression type in template: {:?}", other),
            }),
        }
    }

    /// Instantiate an abstract function by looking up its binding
    fn instantiate_abstract_function(&self, func: &ScalarFunction) -> Result<Vec<Expr>, RuleError> {
        // Templates should only contain abstract functions, not concrete ones
        let abstract_func = Self::as_abstract_function(func)
            .ok_or_else(|| RuleError::InvalidPattern {
                reason: format!("Concrete function '{}' found in template - only abstract functions are allowed", func.name()),
            })?;

        // Instantiate all arguments to build the context
        // The context maps column names that appear in the function's arguments
        // to their instantiated expressions
        let mut context = HashMap::new();
        for arg in &func.args {
            for expr in self.instantiate_expr(arg)? {
                // Get the qualified name of the expression
                let (_table_ref, name) = expr.qualified_name();
                context.insert(name, expr);
            }
        }

        // Get the bound expressions for this function
        let bound_exprs =
            self.functions
                .get(&abstract_func.name)
                .ok_or_else(|| RuleError::UnboundSymbol {
                    symbol: abstract_func.name.clone(),
                })?;

        // Transform each bound expression by replacing column references with context expressions
        bound_exprs
            .iter()
            .map(|expr| self.replace_columns_with_context(expr, &context))
            .collect::<Result<_, _>>()
    }

    /// Replace column references in an expression with expressions from the context
    #[allow(clippy::only_used_in_recursion)]
    fn replace_columns_with_context(
        &self,
        expr: &Expr,
        context: &HashMap<String, Expr>,
    ) -> Result<Expr, RuleError> {
        match expr {
            Expr::Column(col) => {
                // Look up this column in the context - it must exist
                context
                    .get(&col.name)
                    .cloned()
                    .ok_or_else(|| RuleError::UnboundSymbol {
                        symbol: col.name.clone(),
                    })
            }
            Expr::BinaryExpr(binary) => {
                // Recursively replace in both operands
                let left = self.replace_columns_with_context(&binary.left, context)?;
                let right = self.replace_columns_with_context(&binary.right, context)?;
                Ok(Expr::BinaryExpr(BinaryExpr::new(
                    Box::new(left),
                    binary.op,
                    Box::new(right),
                )))
            }
            Expr::ScalarFunction(func) => {
                // Recursively replace in all arguments
                let mut new_args = Vec::new();
                for arg in &func.args {
                    new_args.push(self.replace_columns_with_context(arg, context)?);
                }
                Ok(Expr::ScalarFunction(ScalarFunction {
                    func: func.func.clone(),
                    args: new_args,
                }))
            }
            Expr::Literal(value, data_type) => {
                // Literals stay as is
                Ok(Expr::Literal(value.clone(), data_type.clone()))
            }
            Expr::Not(inner) => {
                let new_inner = self.replace_columns_with_context(inner, context)?;
                Ok(Expr::Not(Box::new(new_inner)))
            }
            Expr::IsNull(inner) => {
                let new_inner = self.replace_columns_with_context(inner, context)?;
                Ok(Expr::IsNull(Box::new(new_inner)))
            }
            Expr::IsNotNull(inner) => {
                let new_inner = self.replace_columns_with_context(inner, context)?;
                Ok(Expr::IsNotNull(Box::new(new_inner)))
            }
            Expr::Negative(inner) => {
                let new_inner = self.replace_columns_with_context(inner, context)?;
                Ok(Expr::Negative(Box::new(new_inner)))
            }
            Expr::Between(between) => {
                let new_expr = self.replace_columns_with_context(&between.expr, context)?;
                let new_low = self.replace_columns_with_context(&between.low, context)?;
                let new_high = self.replace_columns_with_context(&between.high, context)?;
                Ok(Expr::Between(datafusion::logical_expr::Between::new(
                    Box::new(new_expr),
                    between.negated,
                    Box::new(new_low),
                    Box::new(new_high),
                )))
            }
            Expr::Case(case) => {
                let new_expr = case
                    .expr
                    .as_ref()
                    .map(|e| self.replace_columns_with_context(e, context))
                    .transpose()?
                    .map(Box::new);

                let mut new_when = Vec::new();
                for (when_expr, then_expr) in &case.when_then_expr {
                    new_when.push((
                        Box::new(self.replace_columns_with_context(when_expr, context)?),
                        Box::new(self.replace_columns_with_context(then_expr, context)?),
                    ));
                }

                let new_else = case
                    .else_expr
                    .as_ref()
                    .map(|e| self.replace_columns_with_context(e, context))
                    .transpose()?
                    .map(Box::new);

                Ok(Expr::Case(datafusion::logical_expr::Case::new(
                    new_expr, new_when, new_else,
                )))
            }
            Expr::Cast(cast) => {
                let new_expr = self.replace_columns_with_context(&cast.expr, context)?;
                Ok(Expr::Cast(datafusion::logical_expr::Cast::new(
                    Box::new(new_expr),
                    cast.data_type.clone(),
                )))
            }
            Expr::TryCast(try_cast) => {
                let new_expr = self.replace_columns_with_context(&try_cast.expr, context)?;
                Ok(Expr::TryCast(datafusion::logical_expr::TryCast::new(
                    Box::new(new_expr),
                    try_cast.data_type.clone(),
                )))
            }
            Expr::Alias(alias) => {
                let new_expr = self.replace_columns_with_context(&alias.expr, context)?;
                Ok(new_expr.alias_qualified(alias.relation.clone(), alias.name.clone()))
            }
            Expr::InList(in_list) => {
                let new_expr = self.replace_columns_with_context(&in_list.expr, context)?;
                let mut new_list = Vec::new();
                for item in &in_list.list {
                    new_list.push(self.replace_columns_with_context(item, context)?);
                }
                Ok(new_expr.in_list(new_list, in_list.negated))
            }
            Expr::Like(like) => {
                let new_expr = self.replace_columns_with_context(&like.expr, context)?;
                let new_pattern = self.replace_columns_with_context(&like.pattern, context)?;
                // Like expressions don't support escape char replacement in the builder API
                // For now, we'll reconstruct it manually if needed
                if like.escape_char.is_some() {
                    return Err(RuleError::InvalidPattern {
                        reason:
                            "LIKE with escape character not yet supported in column replacement"
                                .to_string(),
                    });
                }
                if like.negated {
                    if like.case_insensitive {
                        Ok(new_expr.not_ilike(new_pattern))
                    } else {
                        Ok(new_expr.not_like(new_pattern))
                    }
                } else if like.case_insensitive {
                    Ok(new_expr.ilike(new_pattern))
                } else {
                    Ok(new_expr.like(new_pattern))
                }
            }
            Expr::SimilarTo(_similar) => {
                // SimilarTo expressions are complex, for now return an error
                // This would need special handling as DataFusion may not have a builder for it
                Err(RuleError::InvalidPattern {
                    reason: "SIMILAR TO expressions not yet supported in column replacement"
                        .to_string(),
                })
            }
            // For other expression types we don't handle yet, return an error
            other => Err(RuleError::InvalidPattern {
                reason: format!(
                    "Expression type {:?} not yet supported in column replacement",
                    other
                ),
            }),
        }
    }

    /// Instantiate a binary expression by recursively transforming operands
    fn instantiate_binary_expr(&self, binary: &BinaryExpr) -> Result<Vec<Expr>, RuleError> {
        // Recursively instantiate left and right operands
        // Each should return exactly one expression
        let mut left_exprs = self.instantiate_expr(&binary.left)?;
        let mut right_exprs = self.instantiate_expr(&binary.right)?;

        if left_exprs.len() != 1 || right_exprs.len() != 1 {
            return Err(RuleError::InvalidPattern {
                reason: "Binary expression operands must resolve to single expressions".to_string(),
            });
        }

        let left = left_exprs.pop().unwrap();
        let right = right_exprs.pop().unwrap();

        Ok(vec![Expr::BinaryExpr(BinaryExpr::new(
            Box::new(left),
            binary.op,
            Box::new(right),
        ))])
    }

    /// Instantiate a column by expanding to all concrete columns in its partition
    fn instantiate_column(&self, column: &Column) -> Result<Vec<Expr>, RuleError> {
        // Get all concrete columns for this abstract field
        let concrete_columns =
            self.fields
                .get(&column.name)
                .ok_or_else(|| RuleError::UnboundSymbol {
                    symbol: column.name.clone(),
                })?;

        // Create column expressions for each concrete column
        let mut columns = concrete_columns.iter().collect::<Vec<_>>();

        // Sort by output schema order if we have one
        columns.sort_by_key(|col| self.output_schema.iter().position(|name| name == &col.name));

        Ok(columns
            .iter()
            .map(|&col| Expr::Column(col.clone()))
            .collect())
    }
}

impl PatternMatcher for DefaultMatcher {
    fn resolve(&mut self, pattern: &Rel, concrete: &LogicalPlan) -> Result<(), RuleError> {
        // Track the output column names of the concrete plan for ordering
        // Get the schema field names from the concrete plan
        let schema = concrete.schema();
        self.output_schema = schema
            .fields()
            .iter()
            .map(|f| f.name().to_string())
            .collect();

        self.resolve_plan(&pattern.plan, concrete)
    }

    fn instantiate(&self, template: &Rel) -> Result<LogicalPlan, RuleError> {
        self.instantiate_plan(&template.plan)
    }
}

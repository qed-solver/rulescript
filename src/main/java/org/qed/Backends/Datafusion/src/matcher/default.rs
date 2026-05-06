use std::{collections::HashMap, fmt::Debug, sync::Arc};

use datafusion::{
    common::{
        Column, JoinConstraint,
        tree_node::{TreeNode, TreeNodeRecursion},
    },
    logical_expr::{
        Aggregate, BinaryExpr, EmptyRelation, Expr, Extension, Filter, Join, LogicalPlan, Operator,
        Projection, Union, build_join_schema,
        expr::{AggregateFunction, AggregateFunctionParams, Exists, InSubquery, ScalarFunction},
        lit,
    },
};

use crate::ast::{
    empty::Empty,
    extension::UserDefinedLogicalPattern,
    pattern::{AggregatePattern, ScalarPattern},
    relational::Rel,
    source::Source,
};

use super::{BindingConflict, PatternMatcher, RuleError};

/// Default pattern matcher that tracks bindings internally
#[derive(Clone, Debug, Default)]
pub struct DefaultMatcher {
    /// Abstract functions/predicates -> list of concrete expressions (usually single)
    functions: HashMap<String, Vec<Expr>>,

    /// Abstract sources -> concrete plan and partitions
    sources: HashMap<String, (LogicalPlan, HashMap<Column, Vec<Column>>)>,

    /// Track the qualified columns of the top-level output schema
    /// Used to preserve column ordering during projection instantiation
    output_schema: Vec<Column>,
}

impl DefaultMatcher {
    /// Create a new empty matcher
    pub fn new() -> Self {
        Self::default()
    }

    // ===== PUBLIC METHODS FOR USER-DEFINED OPERATORS =====

    /// Store context for a user-defined pattern
    ///
    /// This is used by user-defined operator implementations to store their
    /// output column mappings after resolution.
    pub fn store_user_defined_context(
        &mut self,
        pattern: &UserDefinedLogicalPattern,
        plan: LogicalPlan,
        context: HashMap<Column, Vec<Column>>,
    ) {
        self.sources.insert(pattern.id(), (plan, context));
    }

    /// Retrieve stored context for a user-defined pattern
    ///
    /// This is used by user-defined operator implementations to retrieve
    /// their previously stored context during instantiation.
    pub fn retrieve_user_defined_context(
        &self,
        pattern: &UserDefinedLogicalPattern,
    ) -> Option<&(LogicalPlan, HashMap<Column, Vec<Column>>)> {
        self.sources.get(&pattern.id())
    }

    // ===== HELPER METHODS =====

    /// Extract a Source from an Extension node
    fn as_source_opt(ext: &Extension) -> Option<&Source> {
        ext.node.as_any().downcast_ref::<Source>()
    }

    /// Extract a UserDefinedLogicalPattern from an Extension node
    fn as_user_defined_pattern(ext: &Extension) -> Option<&UserDefinedLogicalPattern> {
        ext.node
            .as_any()
            .downcast_ref::<UserDefinedLogicalPattern>()
    }

    /// Extract a ScalarPattern from a DataFusion ScalarFunction
    fn as_scalar_pattern_opt(func: &ScalarFunction) -> Option<&ScalarPattern> {
        func.func.inner().as_any().downcast_ref::<ScalarPattern>()
    }

    /// Extract an AggregatePattern from a DataFusion AggregateFunction
    fn as_aggregate_pattern_opt(agg_func: &AggregateFunction) -> Option<&AggregatePattern> {
        agg_func
            .func
            .inner()
            .as_any()
            .downcast_ref::<AggregatePattern>()
    }

    /// Extract an Empty from an Extension node
    fn as_empty_opt(ext: &Extension) -> Option<&Empty> {
        ext.node.as_any().downcast_ref::<Empty>()
    }

    /// Extract complete filter from a join
    fn extract_complete_filter(join: &Join) -> Expr {
        join.on
            .iter()
            .cloned()
            .map(|(left, right)| left.eq(right))
            .chain(join.filter.clone())
            .reduce(|acc, expr| acc.and(expr))
            .unwrap_or(lit(true))
    }

    /// Generic partitioning routine: assign each item to first matching pattern
    /// Uses two-phase approach to prevent state pollution from failed matches:
    /// Phase 1: Try matches on clones to discover which pattern matches each item
    /// Phase 2: Commit successful matches to the real self
    fn partition<P, I: Clone + Debug>(
        &mut self,
        patterns: impl IntoIterator<Item = P>,
        items: impl IntoIterator<Item = I>,
        mut try_match: impl FnMut(&mut Self, &P, &I) -> Result<(), RuleError>,
    ) -> Result<Vec<(P, Vec<I>)>, RuleError> {
        let mut partitions = patterns
            .into_iter()
            .map(|p| (p, Vec::new()))
            .collect::<Vec<_>>();

        let items = items.into_iter().collect::<Vec<_>>();

        // Phase 1: Discovery - try matches on clones to avoid state pollution
        for item in &items {
            let mut matched = false;

            for (pattern, partition) in &mut partitions {
                // Try matching on a clone - failed matches won't pollute self
                let mut cloned_self = self.clone();
                if try_match(&mut cloned_self, pattern, item).is_ok() {
                    partition.push(item.clone());
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

        // Phase 2: Commit - replay all successful matches on the real self
        for (pattern, items) in &partitions {
            for item in items {
                try_match(self, pattern, item)?;
            }
        }

        Ok(partitions)
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
    ///
    /// This is public so user-defined operators can resolve sub-patterns and get output contexts.
    pub fn resolve_plan(
        &mut self,
        pattern: &LogicalPlan,
        concrete: &LogicalPlan,
    ) -> Result<HashMap<Column, Vec<Column>>, RuleError> {
        match (pattern, concrete) {
            // Extension nodes can be Source, Empty, or UserDefinedLogicalPattern
            (LogicalPlan::Extension(ext), con_plan) => {
                // Try Source first (existing behavior)
                if let Some(source) = Self::as_source_opt(ext) {
                    self.resolve_source(source, con_plan)
                }
                // Try Empty - must match against EmptyRelation
                else if let Some(empty) = Self::as_empty_opt(ext) {
                    self.resolve_empty(empty, con_plan)
                }
                // Try UserDefinedLogicalPattern
                else if let Some(user_pattern) = Self::as_user_defined_pattern(ext) {
                    self.resolve_user_defined(user_pattern, con_plan)
                } else {
                    Err(RuleError::StructureMismatch {
                        pattern: Box::new(pattern.clone()),
                        target: Box::new(concrete.clone()),
                    })
                }
            }

            // Filter patterns match Filter nodes
            (LogicalPlan::Filter(pat_filter), LogicalPlan::Filter(con_filter)) => {
                self.resolve_filter(pat_filter, con_filter)
            }

            // Projection patterns match Projection nodes
            (LogicalPlan::Projection(pat_proj), LogicalPlan::Projection(con_proj)) => {
                self.resolve_projection(pat_proj, con_proj)
            }

            // Aggregate patterns match Aggregate nodes
            (LogicalPlan::Aggregate(pat_agg), LogicalPlan::Aggregate(con_agg)) => {
                self.resolve_aggregate(pat_agg, con_agg)
            }

            // Join patterns match Join nodes
            (LogicalPlan::Join(pat_join), LogicalPlan::Join(con_join)) => {
                self.resolve_join(pat_join, con_join)
            }

            // Union patterns match Union nodes (binary only)
            (LogicalPlan::Union(pat_union), LogicalPlan::Union(con_union)) => {
                self.resolve_union(pat_union, con_union)
            }

            // Structure mismatch
            _ => Err(RuleError::StructureMismatch {
                pattern: Box::new(pattern.clone()),
                target: Box::new(concrete.clone()),
            }),
        }
    }

    /// Resolve a Source pattern against any concrete plan
    fn resolve_source(
        &mut self,
        source: &Source,
        concrete: &LogicalPlan,
    ) -> Result<HashMap<Column, Vec<Column>>, RuleError> {
        // Store original concrete plan for instantiation later (needs to be cloned for storage)
        // Check for consistency if already bound
        if let Some((existing, _)) = self.sources.get(&source.table_name)
            && existing != concrete
        {
            return Err(RuleError::InconsistentBinding {
                symbol: source.table_name.clone(),
                details: BindingConflict::Plan {
                    previous: Box::new(existing.clone()),
                    attempted: Box::new(concrete.clone()),
                },
            });
        }

        let source_plan = LogicalPlan::Extension(Extension {
            node: Arc::new(source.clone()),
        });

        let abstract_field_column = source_plan
            .schema()
            .fields()
            .iter()
            .zip(source_plan.schema().columns());
        let concrete_field_column = concrete
            .schema()
            .fields()
            .iter()
            .zip(concrete.schema().columns());

        let partitions = self.partition(
            abstract_field_column,
            concrete_field_column,
            |_, (abstract_field, _), (concrete_field, _)| {
                // Check nullable property:
                // - If pattern field is non-nullable, concrete must also be non-nullable
                // - If pattern field is nullable, concrete can be either nullable or non-nullable
                if !abstract_field.is_nullable() && concrete_field.is_nullable() {
                    Err(RuleError::StructureMismatch {
                        pattern: Box::new(LogicalPlan::Extension(Extension {
                            node: Arc::new(source.clone()),
                        })),
                        target: Box::new(concrete.clone()),
                    })
                } else {
                    Ok(())
                }
            },
        )?;

        let context = partitions
            .into_iter()
            .map(|((_, abstract_column), partition)| {
                (
                    abstract_column,
                    partition
                        .into_iter()
                        .map(|(_, concrete_column)| concrete_column)
                        .collect(),
                )
            })
            .collect::<HashMap<_, _>>();

        self.sources.insert(
            source.table_name.clone(),
            (concrete.clone(), context.clone()),
        );

        Ok(context)
    }

    /// Resolve an Empty pattern against a concrete EmptyRelation
    fn resolve_empty(
        &mut self,
        empty: &Empty,
        concrete: &LogicalPlan,
    ) -> Result<HashMap<Column, Vec<Column>>, RuleError> {
        // Empty must match against EmptyRelation
        let LogicalPlan::EmptyRelation(_) = concrete else {
            return Err(RuleError::StructureMismatch {
                pattern: Box::new(LogicalPlan::Extension(Extension {
                    node: Arc::new(empty.clone()),
                })),
                target: Box::new(concrete.clone()),
            });
        };

        // Resolve the inner pattern against the concrete EmptyRelation
        // This captures schema bindings (e.g., source -> concrete schema)
        self.resolve_plan(&empty.inner, concrete)
    }

    /// Resolve a UserDefinedLogicalPattern against concrete plan
    fn resolve_user_defined(
        &mut self,
        pattern: &UserDefinedLogicalPattern,
        concrete: &LogicalPlan,
    ) -> Result<HashMap<Column, Vec<Column>>, RuleError> {
        // Delegate to user implementation (must store context via store_user_defined_context)
        pattern.implementation().resolve(pattern, concrete, self)?;

        // Retrieve the stored context
        let (_plan, context) = self.retrieve_user_defined_context(pattern).ok_or_else(|| {
            RuleError::UnboundSymbol {
                symbol: format!(
                    "user-defined pattern '{}' did not store context during resolve",
                    pattern.implementation().operator_name()
                ),
            }
        })?;

        Ok(context.clone())
    }

    /// Resolve a Filter pattern against a concrete Filter
    fn resolve_filter(
        &mut self,
        pattern: &Filter,
        concrete: &Filter,
    ) -> Result<HashMap<Column, Vec<Column>>, RuleError> {
        // First, recursively match the inputs
        let context = self.resolve_plan(&pattern.input, &concrete.input)?;

        // Match the filter predicate expression
        self.resolve_expr(&pattern.predicate, &concrete.predicate, &context)?;

        Ok(context)
    }

    /// Resolve a Projection pattern against a concrete Projection
    fn resolve_projection(
        &mut self,
        pattern: &Projection,
        concrete: &Projection,
    ) -> Result<HashMap<Column, Vec<Column>>, RuleError> {
        // First, recursively match the inputs
        let input_context = self.resolve_plan(&pattern.input, &concrete.input)?;

        // Then, partition the projection expressions
        let partitions = self.partition(
            &pattern.expr,
            LogicalPlan::Projection(concrete.clone())
                .schema()
                .columns()
                .into_iter()
                .zip(&concrete.expr),
            |matcher, pat_expr, (_, con_expr)| {
                matcher.resolve_expr(pat_expr, con_expr, &input_context)
            },
        )?;

        // Finally, construct the context based on partition
        let mut output_context = HashMap::new();
        for (pat_col, (pat_expr, con_exprs)) in LogicalPlan::Projection(pattern.clone())
            .schema()
            .columns()
            .into_iter()
            .zip(partitions)
        {
            let con_cols = con_exprs
                .into_iter()
                .map(|(con_col, _)| con_col.clone())
                .collect::<Vec<_>>();
            if let Expr::Column(expr_col) = pat_expr {
                // Get the list of expected columns for this abstract field
                let expected_cols =
                    input_context
                        .get(expr_col)
                        .ok_or_else(|| RuleError::UnboundSymbol {
                            symbol: expr_col.name.clone(),
                        })?;

                // Check if concrete column is in the allowed list
                if &con_cols != expected_cols {
                    return Err(RuleError::UnboundSymbol {
                        symbol: expr_col.name.clone(),
                    });
                }
            }
            output_context.insert(pat_col, con_cols);
        }
        Ok(output_context)
    }

    /// Resolve an Aggregate pattern against a concrete Aggregate
    fn resolve_aggregate(
        &mut self,
        pattern: &Aggregate,
        concrete: &Aggregate,
    ) -> Result<HashMap<Column, Vec<Column>>, RuleError> {
        // First, recursively match the inputs
        let input_context = self.resolve_plan(&pattern.input, &concrete.input)?;

        // Partition the GROUP BY expressions
        let group_partitions = self.partition(
            &pattern.group_expr,
            LogicalPlan::Aggregate(concrete.clone())
                .schema()
                .columns()
                .into_iter()
                .take(concrete.group_expr.len()) // GROUP BY columns come first
                .zip(&concrete.group_expr),
            |matcher, pat_expr, (_, con_expr)| {
                matcher.resolve_expr(pat_expr, con_expr, &input_context)
            },
        )?;

        // Partition the aggregate expressions
        let agg_partitions = self.partition(
            &pattern.aggr_expr,
            LogicalPlan::Aggregate(concrete.clone())
                .schema()
                .columns()
                .into_iter()
                .skip(concrete.group_expr.len()) // Aggregate columns come after GROUP BY
                .zip(&concrete.aggr_expr),
            |matcher, pat_expr, (_, con_expr)| {
                matcher.resolve_expr(pat_expr, con_expr, &input_context)
            },
        )?;

        // Build output context: GROUP BY columns + aggregate result columns
        let mut output_context = HashMap::new();

        // Add GROUP BY columns to context
        for (pat_col, (pat_expr, con_exprs)) in LogicalPlan::Aggregate(pattern.clone())
            .schema()
            .columns()
            .into_iter()
            .take(pattern.group_expr.len())
            .zip(group_partitions)
        {
            let con_cols = con_exprs
                .into_iter()
                .map(|(con_col, _)| con_col.clone())
                .collect::<Vec<_>>();

            // Validate column references for GROUP BY expressions
            if let Expr::Column(expr_col) = pat_expr {
                let expected_cols =
                    input_context
                        .get(expr_col)
                        .ok_or_else(|| RuleError::UnboundSymbol {
                            symbol: expr_col.name.clone(),
                        })?;

                if &con_cols != expected_cols {
                    return Err(RuleError::UnboundSymbol {
                        symbol: expr_col.name.clone(),
                    });
                }
            }
            output_context.insert(pat_col, con_cols);
        }

        // Add aggregate result columns to context
        for (pat_col, (_, con_exprs)) in LogicalPlan::Aggregate(pattern.clone())
            .schema()
            .columns()
            .into_iter()
            .skip(pattern.group_expr.len())
            .zip(agg_partitions)
        {
            let con_cols = con_exprs
                .into_iter()
                .map(|(con_col, _)| con_col.clone())
                .collect::<Vec<_>>();
            output_context.insert(pat_col, con_cols);
        }

        Ok(output_context)
    }

    /// Resolve a Join pattern against a concrete Join
    fn resolve_join(
        &mut self,
        pattern: &Join,
        concrete: &Join,
    ) -> Result<HashMap<Column, Vec<Column>>, RuleError> {
        // Match join type exactly (Inner/Left/Right changes semantics)
        if pattern.join_type != concrete.join_type {
            return Err(RuleError::StructureMismatch {
                pattern: Box::new(LogicalPlan::Join(pattern.clone())),
                target: Box::new(LogicalPlan::Join(concrete.clone())),
            });
        }

        // Recursively match left and right inputs
        let left_context = self.resolve_plan(&pattern.left, &concrete.left)?;
        let right_context = self.resolve_plan(&pattern.right, &concrete.right)?;

        // Merge contexts for join condition (can reference both sides)
        let mut merged_context = left_context.clone();
        merged_context.extend(right_context.clone());

        // Reconstruct full join predicate expression from pattern join
        let full_pattern_predicate = Self::extract_complete_filter(pattern);

        // Extract complete filter predicate from concrete plan
        let full_concrete_predicate = Self::extract_complete_filter(concrete);

        // Match pattern predicate against reconstructed predicate expressions
        self.resolve_expr(
            &full_pattern_predicate,
            &full_concrete_predicate,
            &merged_context,
        )?;

        Ok(merged_context)
    }

    /// Resolve a Union pattern against a concrete Union (binary only)
    fn resolve_union(
        &mut self,
        pattern: &Union,
        concrete: &Union,
    ) -> Result<HashMap<Column, Vec<Column>>, RuleError> {
        // Only support binary unions for now
        if pattern.inputs.len() != 2 || concrete.inputs.len() != 2 {
            return Err(RuleError::StructureMismatch {
                pattern: Box::new(LogicalPlan::Union(pattern.clone())),
                target: Box::new(LogicalPlan::Union(concrete.clone())),
            });
        }

        // Recursively match left and right inputs
        let left_context = self.resolve_plan(&pattern.inputs[0], &concrete.inputs[0])?;
        let right_context = self.resolve_plan(&pattern.inputs[1], &concrete.inputs[1])?;

        // Merge contexts (union columns come from either branch with same schema)
        let mut merged_context = left_context;
        merged_context.extend(right_context);

        Ok(merged_context)
    }

    /// Main dispatcher for resolving expressions
    ///
    /// This is public so user-defined operators can resolve expressions within a given context.
    pub fn resolve_expr(
        &mut self,
        pattern: &Expr,
        concrete: &Expr,
        context: &HashMap<Column, Vec<Column>>,
    ) -> Result<(), RuleError> {
        match (pattern, concrete) {
            // Scalar pattern can match any expression
            (Expr::ScalarFunction(pat_func), con_expr) => {
                // Try to get our ScalarPattern from the ScalarFunction
                let scalar_pattern = Self::as_scalar_pattern_opt(pat_func).ok_or_else(|| {
                    RuleError::ExpressionMismatch {
                        pattern: Box::new(pattern.clone()),
                        target: Box::new(con_expr.clone()),
                    }
                })?;
                self.resolve_pattern(&scalar_pattern.name, pattern, con_expr, context)
            }

            // Aggregate pattern can match any expression containing aggregates
            (Expr::AggregateFunction(pat_agg), con_expr) => {
                // Try to get our AggregatePattern
                let aggregate_pattern =
                    Self::as_aggregate_pattern_opt(pat_agg).ok_or_else(|| {
                        RuleError::ExpressionMismatch {
                            pattern: Box::new(pattern.clone()),
                            target: Box::new(con_expr.clone()),
                        }
                    })?;
                self.resolve_pattern(&aggregate_pattern.name, pattern, con_expr, context)
            }

            // Binary expressions
            (Expr::BinaryExpr(pat_binary), Expr::BinaryExpr(con_binary)) => {
                self.resolve_binary_expr(pat_binary, con_binary, context)
            }

            // Columns must match exactly
            (Expr::Column(pat_col), Expr::Column(con_col)) => {
                self.resolve_column(pat_col, con_col, context)
            }

            // Literals must match exactly
            (Expr::Literal(pat_val, _), Expr::Literal(con_val, _)) => {
                if pat_val == con_val {
                    Ok(())
                } else {
                    Err(RuleError::ExpressionMismatch {
                        pattern: Box::new(pattern.clone()),
                        target: Box::new(concrete.clone()),
                    })
                }
            }

            // Alias in pattern - resolve inner pattern against concrete (which may or may not be aliased)
            (Expr::Alias(pat_alias), concrete_expr) => {
                self.resolve_expr(&pat_alias.expr, concrete_expr, context)
            }

            // TODO: Handle other expression types as needed
            _ => Err(RuleError::ExpressionMismatch {
                pattern: Box::new(pattern.clone()),
                target: Box::new(concrete.clone()),
            }),
        }
    }

    /// Resolve a pattern (scalar or aggregate) against a concrete expression
    fn resolve_pattern(
        &mut self,
        pattern_name: &str,
        pattern: &Expr,
        concrete: &Expr,
        context: &HashMap<Column, Vec<Column>>,
    ) -> Result<(), RuleError> {
        // Get pattern's column references
        let pattern_columns = pattern.column_refs();

        // Get concrete expression's column references (direct columns only)
        let direct_columns = concrete.column_refs();

        // Also collect outer reference columns from subqueries anywhere in the expression.
        // These represent dependencies on outer scope that must be considered
        // when partitioning predicates for push-down rules.
        // Note: outer_ref_columns already contains all outer refs recursively (including nested subqueries).
        let mut outer_ref_columns: Vec<Column> = Vec::new();
        let _ = concrete.apply(|e| {
            let outer_refs = match e {
                Expr::ScalarSubquery(sq) => &sq.outer_ref_columns,
                Expr::InSubquery(InSubquery { subquery, .. }) => &subquery.outer_ref_columns,
                Expr::Exists(Exists { subquery, .. }) => &subquery.outer_ref_columns,
                _ => return Ok(TreeNodeRecursion::Continue),
            };
            for outer_ref in outer_refs {
                if let Expr::OuterReferenceColumn(_, col) = outer_ref {
                    outer_ref_columns.push(col.clone());
                }
            }
            Ok(TreeNodeRecursion::Continue)
        });

        // Combine direct columns and outer ref columns for partitioning
        let all_columns: Vec<&Column> = direct_columns
            .iter()
            .copied()
            .chain(outer_ref_columns.iter())
            .collect();

        // Use partition to ensure each concrete column matches some pattern column
        // This enforces that concrete expression only uses columns from allowed partitions
        self.partition(
            &pattern_columns,
            all_columns.iter(),
            |matcher, pat_col, con_col| matcher.resolve_column(pat_col, con_col, context),
        )?;

        // Bind this pattern to the entire concrete expression
        self.functions
            .entry(pattern_name.to_string())
            .or_default()
            .push(concrete.clone());

        Ok(())
    }

    /// Resolve binary expressions
    fn resolve_binary_expr(
        &mut self,
        pat_binary: &BinaryExpr,
        con_binary: &BinaryExpr,
        context: &HashMap<Column, Vec<Column>>,
    ) -> Result<(), RuleError> {
        match pat_binary.op {
            Operator::And | Operator::Or => {
                // For commutative AND/OR, flatten and use partition_items
                let pattern_expr = Expr::BinaryExpr(pat_binary.clone());
                let pattern_terms = Self::flatten_binary_op(&pattern_expr, &pat_binary.op);

                let concrete_expr = Expr::BinaryExpr(con_binary.clone());
                let concrete_terms = Self::flatten_binary_op(&concrete_expr, &pat_binary.op);

                // Use partition to match concrete terms to pattern terms
                self.partition(
                    &pattern_terms,
                    concrete_terms,
                    |matcher, pat_term, con_term| matcher.resolve_expr(pat_term, con_term, context),
                )?;

                // After partition, consolidate multiple bindings for patterns
                // that appeared in this flattened expression
                for pat_term in &pattern_terms {
                    let Expr::ScalarFunction(func) = pat_term else {
                        continue;
                    };
                    let Some(scalar_pattern) = Self::as_scalar_pattern_opt(func) else {
                        continue;
                    };
                    let Some(exprs) = self.functions.remove(&scalar_pattern.name) else {
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
                        .insert(scalar_pattern.name.clone(), vec![combined]);
                }

                Ok(())
            }
            op => {
                // For unknown operators, they must match exactly
                if op != con_binary.op {
                    return Err(RuleError::ExpressionMismatch {
                        pattern: Box::new(Expr::BinaryExpr(pat_binary.clone())),
                        target: Box::new(Expr::BinaryExpr(con_binary.clone())),
                    });
                }
                // Match structurally
                self.resolve_expr(&pat_binary.left, &con_binary.left, context)?;
                self.resolve_expr(&pat_binary.right, &con_binary.right, context)
            }
        }
    }

    /// Resolve a column reference (for non-projection contexts like filters)
    fn resolve_column(
        &mut self,
        pat_col: &Column,
        con_col: &Column,
        context: &HashMap<Column, Vec<Column>>,
    ) -> Result<(), RuleError> {
        // Get the list of allowed columns for this abstract field
        let allowed_columns = context
            .get(pat_col)
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
    ///
    /// This is public so user-defined operators can instantiate sub-patterns and get output contexts.
    pub fn instantiate_plan(
        &mut self,
        template: &LogicalPlan,
    ) -> Result<(LogicalPlan, HashMap<Column, Vec<Column>>), RuleError> {
        match template {
            // Extension nodes can be Source, Empty, or UserDefinedLogicalPattern
            LogicalPlan::Extension(ext) => {
                // Try Source first
                if let Some(source) = Self::as_source_opt(ext) {
                    self.instantiate_source(source)
                }
                // Try Empty
                else if let Some(empty) = Self::as_empty_opt(ext) {
                    self.instantiate_empty(empty)
                }
                // Try UserDefinedLogicalPattern
                else if let Some(user_pattern) = Self::as_user_defined_pattern(ext) {
                    self.instantiate_user_defined(user_pattern)
                } else {
                    Err(RuleError::InvalidPattern {
                        reason: format!("Unknown extension node: {}", ext.node.name()),
                    })
                }
            }

            // Filter: instantiate input and predicate
            LogicalPlan::Filter(filter) => self.instantiate_filter(filter),

            // Projection: instantiate input and expressions
            LogicalPlan::Projection(projection) => self.instantiate_projection(projection),

            // Aggregate: instantiate input and expressions
            LogicalPlan::Aggregate(aggregate) => self.instantiate_aggregate(aggregate),

            // Join: instantiate inputs and conditions
            LogicalPlan::Join(join) => self.instantiate_join(join),

            // Union: instantiate inputs
            LogicalPlan::Union(union) => self.instantiate_union(union),

            // Other plan types should not appear in templates
            other => Err(RuleError::InvalidPattern {
                reason: format!("Unsupported plan type in template: {:?}", other),
            }),
        }
    }

    /// Instantiate a Source pattern by looking up the bound plan
    fn instantiate_source(
        &mut self,
        source: &Source,
    ) -> Result<(LogicalPlan, HashMap<Column, Vec<Column>>), RuleError> {
        // Look up the source by table name in our bindings
        self.sources
            .get(&source.table_name)
            .cloned()
            .ok_or_else(|| RuleError::UnboundSymbol {
                symbol: source.table_name.clone(),
            })
    }

    /// Instantiate a UserDefinedLogicalPattern
    fn instantiate_user_defined(
        &mut self,
        pattern: &UserDefinedLogicalPattern,
    ) -> Result<(LogicalPlan, HashMap<Column, Vec<Column>>), RuleError> {
        // Delegate to user implementation
        let plan = pattern.implementation().instantiate(self)?;

        // Retrieve the stored context from resolution
        let (_stored_plan, context) =
            self.retrieve_user_defined_context(pattern).ok_or_else(|| {
                RuleError::UnboundSymbol {
                    symbol: format!(
                        "user-defined pattern '{}' context not found during instantiate",
                        pattern.implementation().operator_name()
                    ),
                }
            })?;

        Ok((plan, context.clone()))
    }

    /// Instantiate a Filter node by transforming input and predicate
    fn instantiate_filter(
        &mut self,
        filter: &Filter,
    ) -> Result<(LogicalPlan, HashMap<Column, Vec<Column>>), RuleError> {
        // First, recursively instantiate the input
        let (input, context) = self.instantiate_plan(&filter.input)?;

        // Instantiate the filter predicate expression
        // For filters, we expect exactly one expression back
        let predicate = self
            .instantiate_expr(&filter.predicate, &context)?
            .pop()
            .ok_or_else(|| RuleError::InvalidPattern {
                reason: "Filter predicate must resolve to a single expression".to_string(),
            })?;

        // Build new Filter node with instantiated components
        Ok((
            LogicalPlan::Filter(Filter::try_new(predicate, Arc::new(input))?),
            context,
        ))
    }

    /// Instantiate a Projection node by transforming input and expressions
    fn instantiate_projection(
        &mut self,
        projection: &Projection,
    ) -> Result<(LogicalPlan, HashMap<Column, Vec<Column>>), RuleError> {
        // First, recursively instantiate the input
        let (input, input_context) = self.instantiate_plan(&projection.input)?;

        // Get template output columns
        let temp_cols = LogicalPlan::Projection(projection.clone())
            .schema()
            .columns();

        // Instantiate all projection expressions
        let mut all_exprs = Vec::new();
        for (temp_expr, temp_col) in projection.expr.iter().zip(temp_cols) {
            all_exprs.extend(
                self.instantiate_expr(temp_expr, &input_context)?
                    .into_iter()
                    .map(|con_expr| (temp_col.clone(), con_expr)),
            );
        }

        // Sort expressions by the original output column order
        all_exprs.sort_by_key(|(_, con_expr)| {
            // If this is a column expression, find its position in the original schema
            if let Expr::Column(col) = con_expr {
                self.output_schema
                    .iter()
                    .position(|output_col| output_col == col)
            } else {
                None
            }
        });

        // Build new Projection node with instantiated and sorted components
        let plan = LogicalPlan::Projection(Projection::try_new(
            all_exprs
                .iter()
                .map(|(_, con_expr)| con_expr)
                .cloned()
                .collect(),
            Arc::new(input),
        )?);

        // Construct output context
        let output_context = all_exprs
            .into_iter()
            .map(|(temp_col, _)| temp_col)
            .zip(plan.schema().columns())
            .fold(
                HashMap::<_, Vec<_>>::new(),
                |mut context, (temp_col, con_col)| {
                    context.entry(temp_col).or_default().push(con_col);
                    context
                },
            );

        Ok((plan, output_context))
    }

    /// Instantiate an Aggregate node by transforming input and expressions
    fn instantiate_aggregate(
        &mut self,
        aggregate: &Aggregate,
    ) -> Result<(LogicalPlan, HashMap<Column, Vec<Column>>), RuleError> {
        // First, recursively instantiate the input
        let (input, input_context) = self.instantiate_plan(&aggregate.input)?;

        // Get template output columns
        let temp_cols = LogicalPlan::Aggregate(aggregate.clone()).schema().columns();

        // Instantiate GROUP BY expressions
        let mut all_group_exprs = Vec::new();
        for (temp_expr, temp_col) in aggregate
            .group_expr
            .iter()
            .zip(temp_cols.iter().take(aggregate.group_expr.len()))
        {
            all_group_exprs.extend(
                self.instantiate_expr(temp_expr, &input_context)?
                    .into_iter()
                    .map(|con_expr| (temp_col.clone(), con_expr)),
            );
        }

        // Sort group expressions by the original output column order
        all_group_exprs.sort_by_key(|(_, con_expr)| {
            if let Expr::Column(col) = con_expr {
                self.output_schema
                    .iter()
                    .position(|output_col| output_col == col)
            } else {
                None
            }
        });

        // Instantiate aggregate expressions
        let mut all_agg_exprs = Vec::new();
        for (temp_expr, temp_col) in aggregate
            .aggr_expr
            .iter()
            .zip(temp_cols.iter().skip(aggregate.group_expr.len()))
        {
            all_agg_exprs.extend(
                self.instantiate_expr(temp_expr, &input_context)?
                    .into_iter()
                    .map(|con_expr| (temp_col.clone(), con_expr)),
            );
        }

        // Build new Aggregate node with instantiated components
        let plan = LogicalPlan::Aggregate(Aggregate::try_new(
            Arc::new(input),
            all_group_exprs
                .iter()
                .map(|(_, con_expr)| con_expr)
                .cloned()
                .collect(),
            all_agg_exprs
                .iter()
                .map(|(_, con_expr)| con_expr)
                .cloned()
                .collect(),
        )?);

        // Construct output context
        let output_context = all_group_exprs
            .into_iter()
            .chain(all_agg_exprs)
            .zip(plan.schema().columns())
            .fold(
                HashMap::<_, Vec<_>>::new(),
                |mut context, ((temp_col, _), con_col)| {
                    context.entry(temp_col).or_default().push(con_col);
                    context
                },
            );

        Ok((plan, output_context))
    }

    /// Instantiate a Join node by transforming inputs and conditions
    fn instantiate_join(
        &mut self,
        join: &Join,
    ) -> Result<(LogicalPlan, HashMap<Column, Vec<Column>>), RuleError> {
        // Recursively instantiate left and right inputs
        let (left_input, left_context) = self.instantiate_plan(&join.left)?;
        let (right_input, right_context) = self.instantiate_plan(&join.right)?;

        // Merge contexts for join output
        let mut merged_context = left_context.clone();
        merged_context.extend(right_context.clone());

        // Reconstruct full join predicate expression from pattern join
        let full_pattern_condition = Self::extract_complete_filter(join);

        // Instantiate the full join predicate expression
        // For joins, we expect exactly one expression back
        let predicate = self
            .instantiate_expr(&full_pattern_condition, &merged_context)?
            .pop()
            .ok_or_else(|| RuleError::InvalidPattern {
                reason: "Filter predicate must resolve to a single expression".to_string(),
            })?;

        // Correlated subqueries cannot be placed in join conditions - they are only
        // allowed in Filter, Projection, and Aggregate nodes. Check if the predicate
        // contains any subqueries with outer references.
        let mut has_correlated_subquery = false;
        let _ = predicate.apply(|e| {
            let is_correlated = match e {
                Expr::ScalarSubquery(sq) => !sq.outer_ref_columns.is_empty(),
                Expr::InSubquery(InSubquery { subquery, .. }) => {
                    !subquery.outer_ref_columns.is_empty()
                }
                Expr::Exists(Exists { subquery, .. }) => !subquery.outer_ref_columns.is_empty(),
                _ => false,
            };
            if is_correlated {
                has_correlated_subquery = true;
            }
            Ok(TreeNodeRecursion::Continue)
        });
        if has_correlated_subquery {
            return Err(RuleError::InvalidPattern {
                reason: "Join condition cannot contain correlated subqueries".to_string(),
            });
        }

        // Build join schema
        let join_schema =
            build_join_schema(left_input.schema(), right_input.schema(), &join.join_type)?;

        // Build new Join node in pre-optimization form:
        // - on: empty (let optimizer extract equijoins)
        // - filter: instantiated predicate
        // - join_constraint: always On
        // - null_equality: from pattern (should be NullEqualsNothing)
        Ok((
            LogicalPlan::Join(Join {
                left: Arc::new(left_input),
                right: Arc::new(right_input),
                on: Vec::new(), // Empty - optimizer will extract equijoins
                filter: Some(predicate),
                join_type: join.join_type,
                join_constraint: JoinConstraint::On, // Always ON
                schema: Arc::new(join_schema),
                null_equality: join.null_equality, // Use pattern's null_equality
            }),
            merged_context,
        ))
    }

    /// Instantiate a Union node by transforming inputs
    fn instantiate_union(
        &mut self,
        union: &Union,
    ) -> Result<(LogicalPlan, HashMap<Column, Vec<Column>>), RuleError> {
        // Only support binary unions
        if union.inputs.len() != 2 {
            return Err(RuleError::InvalidPattern {
                reason: format!(
                    "Union pattern must have exactly 2 inputs, found {}",
                    union.inputs.len()
                ),
            });
        }

        // Recursively instantiate left and right inputs
        let (left_input, left_context) = self.instantiate_plan(&union.inputs[0])?;
        let (right_input, right_context) = self.instantiate_plan(&union.inputs[1])?;

        // Merge contexts
        let mut merged_context = left_context;
        merged_context.extend(right_context);

        // Build new Union node
        let union_schema = left_input.schema().clone();
        Ok((
            LogicalPlan::Union(Union {
                inputs: vec![Arc::new(left_input), Arc::new(right_input)],
                schema: union_schema,
            }),
            merged_context,
        ))
    }

    /// Instantiate an Empty pattern by instantiating its inner plan and using that schema
    fn instantiate_empty(
        &mut self,
        empty: &Empty,
    ) -> Result<(LogicalPlan, HashMap<Column, Vec<Column>>), RuleError> {
        // Instantiate the inner plan to get the concrete schema
        let (inner_plan, _context) = self.instantiate_plan(&empty.inner)?;

        // Create an EmptyRelation with the concrete schema from the instantiated inner plan
        Ok((
            LogicalPlan::EmptyRelation(EmptyRelation {
                produce_one_row: false,
                schema: inner_plan.schema().clone(),
            }),
            HashMap::new(),
        ))
    }

    /// Main dispatcher for instantiating expressions
    /// Returns a Vec because patterns can expand to multiple expressions
    ///
    /// This is public so user-defined operators can instantiate expressions.
    pub fn instantiate_expr(
        &self,
        template: &Expr,
        context: &HashMap<Column, Vec<Column>>,
    ) -> Result<Vec<Expr>, RuleError> {
        match template {
            // Scalar pattern: look up in bindings and return all bound expressions
            Expr::ScalarFunction(func) => {
                let scalar_pattern = Self::as_scalar_pattern_opt(func)
                    .ok_or_else(|| RuleError::InvalidPattern {
                        reason: format!("Concrete function '{}' found in template - only pattern functions are allowed", func.name()),
                    })?;
                self.instantiate_pattern(&scalar_pattern.name, &func.args, context)
            }

            // Aggregate pattern: look up in bindings
            Expr::AggregateFunction(agg_func) => {
                let aggregate_pattern = Self::as_aggregate_pattern_opt(agg_func)
                    .ok_or_else(|| RuleError::InvalidPattern {
                        reason: format!("Concrete aggregate function '{}' found in template - only pattern functions are allowed", agg_func.func.name()),
                    })?;
                self.instantiate_pattern(&aggregate_pattern.name, &agg_func.params.args, context)
            }

            // Binary expression: recursively instantiate operands (but stay as single expr)
            Expr::BinaryExpr(binary) => self.instantiate_binary_expr(binary, context),

            // Column: expand to all concrete columns in the partition
            Expr::Column(column) => self.instantiate_column(column, context),

            // Literal: pass through as-is
            Expr::Literal(_, _) => Ok(vec![template.clone()]),

            // Alias: instantiate inner expression without re-wrapping
            // By symmetry with resolve_expr which unwraps aliases during matching,
            // we should unwrap aliases during instantiation and let DataFusion
            // generate natural column names
            Expr::Alias(alias) => self.instantiate_expr(&alias.expr, context),

            // TODO: Handle other expression types as needed
            // Error on unexpected patterns instead of passing through
            other => Err(RuleError::InvalidPattern {
                reason: format!("Unsupported expression type in template: {:?}", other),
            }),
        }
    }

    /// Instantiate a pattern (scalar or aggregate) by looking up its binding
    fn instantiate_pattern(
        &self,
        pattern_name: &str,
        args: &[Expr],
        context: &HashMap<Column, Vec<Column>>,
    ) -> Result<Vec<Expr>, RuleError> {
        // Instantiate all arguments to build the evaluation context
        // The context maps qualified names (table_ref, column_name) to their instantiated expressions
        // Using the full qualified name ensures we can distinguish between columns with the same name
        // from different tables (e.g., emp.deptno vs dept.deptno)
        let mut eval_context: HashMap<(Option<String>, String), Expr> = HashMap::new();
        for arg in args {
            for expr in self.instantiate_expr(arg, context)? {
                // Use the qualified name as the key for all expressions
                let (table_ref, name) = expr.qualified_name();
                let key = (table_ref.map(|t| t.to_string()), name);
                eval_context.insert(key, expr);
            }
        }

        // Get the bound expressions for this pattern
        let bound_exprs =
            self.functions
                .get(pattern_name)
                .ok_or_else(|| RuleError::UnboundSymbol {
                    symbol: pattern_name.to_string(),
                })?;

        // Transform each bound expression by replacing column references with context expressions
        bound_exprs
            .iter()
            .map(|expr| self.replace_columns_with_context(expr, &eval_context))
            .collect::<Result<_, _>>()
    }

    /// Replace column references in an expression with expressions from the context
    #[allow(clippy::only_used_in_recursion)]
    fn replace_columns_with_context(
        &self,
        expr: &Expr,
        context: &HashMap<(Option<String>, String), Expr>,
    ) -> Result<Expr, RuleError> {
        match expr {
            Expr::Column(col) => {
                // Look up this column in the context using its qualified name
                let (table_ref, name) = Expr::Column(col.clone()).qualified_name();
                let key = (table_ref.map(|t| t.to_string()), name.clone());

                context
                    .get(&key)
                    .cloned()
                    .ok_or_else(|| RuleError::UnboundSymbol {
                        symbol: format!("{:?}", key),
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
            Expr::AggregateFunction(agg_func) => {
                // Recursively replace columns in all arguments
                let mut new_args = Vec::new();
                for arg in &agg_func.params.args {
                    new_args.push(self.replace_columns_with_context(arg, context)?);
                }

                // Recursively replace columns in filter if present
                let new_filter = agg_func
                    .params
                    .filter
                    .as_ref()
                    .map(|f| self.replace_columns_with_context(f, context))
                    .transpose()?
                    .map(Box::new);

                // Recursively replace columns in order_by expressions if present
                let new_order_by: Result<Vec<_>, _> = agg_func
                    .params
                    .order_by
                    .iter()
                    .map(|sort_expr| {
                        self.replace_columns_with_context(&sort_expr.expr, context)
                            .map(|new_expr| datafusion::logical_expr::SortExpr {
                                expr: new_expr,
                                asc: sort_expr.asc,
                                nulls_first: sort_expr.nulls_first,
                            })
                    })
                    .collect();
                let new_order_by = new_order_by?;

                Ok(Expr::AggregateFunction(AggregateFunction {
                    func: agg_func.func.clone(),
                    params: AggregateFunctionParams {
                        args: new_args,
                        distinct: agg_func.params.distinct,
                        filter: new_filter,
                        order_by: new_order_by,
                        null_treatment: agg_func.params.null_treatment,
                    },
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
            // Subquery expressions - these are self-contained with their own scope
            // The outer_ref_columns reference concrete columns that don't need remapping
            // in transformations like FilterIntoJoin that just move predicates around
            Expr::ScalarSubquery(sq) => {
                // Clone as-is - subquery is self-contained
                Ok(Expr::ScalarSubquery(sq.clone()))
            }
            Expr::InSubquery(isq) => {
                // The expr (left side of IN) needs replacement, subquery is self-contained
                let new_expr = self.replace_columns_with_context(&isq.expr, context)?;
                Ok(Expr::InSubquery(
                    datafusion::logical_expr::expr::InSubquery::new(
                        Box::new(new_expr),
                        isq.subquery.clone(),
                        isq.negated,
                    ),
                ))
            }
            Expr::Exists(ex) => {
                // No outer expression to replace, just clone
                Ok(Expr::Exists(ex.clone()))
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
    fn instantiate_binary_expr(
        &self,
        binary: &BinaryExpr,
        context: &HashMap<Column, Vec<Column>>,
    ) -> Result<Vec<Expr>, RuleError> {
        // Recursively instantiate left and right operands
        // Each should return exactly one expression
        let mut left_exprs = self.instantiate_expr(&binary.left, context)?;
        let mut right_exprs = self.instantiate_expr(&binary.right, context)?;

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
    fn instantiate_column(
        &self,
        column: &Column,
        context: &HashMap<Column, Vec<Column>>,
    ) -> Result<Vec<Expr>, RuleError> {
        // Identify the column partition from context
        let columns = context
            .get(column)
            .cloned()
            .ok_or_else(|| RuleError::UnboundSymbol {
                symbol: column.name.clone(),
            })?;
        Ok(columns.into_iter().map(Expr::Column).collect())
    }
}

impl PatternMatcher for DefaultMatcher {
    fn resolve(&mut self, pattern: &Rel, concrete: &LogicalPlan) -> Result<(), RuleError> {
        // Track the output columns of the concrete plan for ordering
        let schema = concrete.schema();
        self.output_schema = schema.columns().into_iter().collect();

        self.resolve_plan(&pattern.plan, concrete)?;
        Ok(())
    }

    fn instantiate(&mut self, template: &Rel) -> Result<LogicalPlan, RuleError> {
        let (plan, _) = self.instantiate_plan(&template.plan)?;
        Ok(plan)
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }

    fn as_any_mut(&mut self) -> &mut dyn std::any::Any {
        self
    }
}

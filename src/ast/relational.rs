use std::{cmp::Ordering, fmt, sync::Arc};

use datafusion::{
    common::{DFSchemaRef, JoinConstraint, NullEquality},
    error::{DataFusionError, Result},
    logical_expr::{
        Aggregate, Distinct, Extension, Filter, Join, JoinType, Limit, LogicalPlan, Projection,
        Sort, SortExpr, Union, UserDefinedLogicalNodeCore, build_join_schema,
    },
    prelude::{Expr, lit},
};

use crate::ast::opaque::Schema;

// Source pattern that can be used as a variant in DataFusion's LogicalPlan
#[derive(Debug, Clone)]
pub struct Source {
    pub table_name: String,
    pub schema: Schema,
    // Cache the converted DataFusion schema
    df_schema: DFSchemaRef,
}

impl Source {
    pub fn new(table_name: String, schema: Schema) -> Self {
        let df_schema = schema.to_datafusion_schema();
        Self {
            table_name,
            schema,
            df_schema,
        }
    }
}

// Manually implement required traits for UserDefinedLogicalNodeCore
impl PartialEq for Source {
    fn eq(&self, other: &Self) -> bool {
        self.table_name == other.table_name && self.schema == other.schema
    }
}

impl Eq for Source {}

impl PartialOrd for Source {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for Source {
    fn cmp(&self, other: &Self) -> Ordering {
        match self.table_name.cmp(&other.table_name) {
            Ordering::Equal => self.schema.cmp(&other.schema),
            ord => ord,
        }
    }
}

impl std::hash::Hash for Source {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.table_name.hash(state);
        self.schema.hash(state);
        // Don't hash df_schema as it's derived from schema
    }
}

impl UserDefinedLogicalNodeCore for Source {
    fn name(&self) -> &str {
        "Source"
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
            "Source: {} [fields: {}]",
            self.table_name,
            self.schema.field_count()
        )
    }

    fn with_exprs_and_inputs(&self, _exprs: Vec<Expr>, _inputs: Vec<LogicalPlan>) -> Result<Self> {
        // Source pattern should not be modified by optimizer
        Err(DataFusionError::Plan(
            "Source should not be modified by optimizer".to_string(),
        ))
    }
}

// A relational pattern is just a wrapper around a DataFusion LogicalPlan
#[derive(Debug, Clone)]
pub struct Rel {
    pub plan: LogicalPlan,
}

impl Rel {
    // Create a source pattern using our custom Source node
    pub fn source(table_name: String, schema: Schema) -> Self {
        let source = Source::new(table_name, schema);

        let plan = LogicalPlan::Extension(Extension {
            node: Arc::new(source),
        });

        Self { plan }
    }

    // Filter the relation with a predicate
    pub fn filter(self, predicate: Expr) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Filter(Filter::try_new(predicate, Arc::new(self.plan))?),
        })
    }

    // Project specific expressions
    pub fn project(self, exprs: Vec<Expr>) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Projection(Projection::try_new(exprs, Arc::new(self.plan))?),
        })
    }

    // Join with another relation
    pub fn join(
        self,
        right: Rel,
        join_type: JoinType,
        on_exprs: Vec<(Expr, Expr)>,
        filter: Option<Expr>,
    ) -> Result<Self> {
        let left_schema = self.plan.schema();
        let right_schema = right.plan.schema();
        let join_schema = build_join_schema(left_schema, right_schema, &join_type)?;

        Ok(Self {
            plan: LogicalPlan::Join(Join {
                left: Arc::new(self.plan),
                right: Arc::new(right.plan),
                on: on_exprs,
                filter,
                join_type,
                join_constraint: JoinConstraint::On,
                schema: Arc::new(join_schema),
                null_equality: NullEquality::NullEqualsNothing,
            }),
        })
    }

    // Union with another relation
    pub fn union(self, other: Rel) -> Result<Self> {
        let union_schema = self.plan.schema().clone();
        Ok(Self {
            plan: LogicalPlan::Union(Union {
                inputs: vec![Arc::new(self.plan), Arc::new(other.plan)],
                schema: union_schema,
            }),
        })
    }

    // Aggregate with grouping and aggregate expressions
    pub fn aggregate(self, group_exprs: Vec<Expr>, agg_exprs: Vec<Expr>) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Aggregate(Aggregate::try_new(
                Arc::new(self.plan),
                group_exprs,
                agg_exprs,
            )?),
        })
    }

    // Remove duplicate rows
    pub fn distinct(self) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Distinct(Distinct::All(Arc::new(self.plan))),
        })
    }

    // Limit the number of rows
    pub fn limit(self, skip: usize, fetch: Option<usize>) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Limit(Limit {
                skip: if skip > 0 {
                    Some(Box::new(lit(skip as i64)))
                } else {
                    None
                },
                fetch: fetch.map(|f| Box::new(lit(f as i64))),
                input: Arc::new(self.plan),
            }),
        })
    }

    // Sort by expressions (converts Expr to SortExpr with default ascending, nulls first)
    pub fn sort(self, exprs: Vec<SortExpr>) -> Result<Self> {
        Ok(Self {
            plan: LogicalPlan::Sort(Sort {
                expr: exprs,
                input: Arc::new(self.plan),
                fetch: None,
            }),
        })
    }
}

// ============================================================================
// Internal helper macros for expression parsing
// ============================================================================

/// Internal helper to parse expression lists using incremental munching
/// Handles: f(x), f(x, y), g(f(x)), g(a, f(x), b), etc.
/// Also handles aliases: x as y, f(x) as result (for projections)
#[doc(hidden)]
#[macro_export]
macro_rules! __parse_exprs {
    // Base case: empty args
    (@accum [] []) => { Vec::<datafusion::prelude::Expr>::new() };

    // Done processing: return accumulated results
    (@accum [$($result:expr),*] []) => { vec![$($result),*] };

    // Munch: function call with alias (with optional comma + rest)
    (@accum [$($result:expr),*] [$func:ident($($args:tt)*) as $alias:ident $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* $crate::__parse_expr!($func($($args)*)).alias(stringify!($alias))] [$($($rest)*)?])
    };

    // Munch: identifier with alias (with optional comma + rest)
    (@accum [$($result:expr),*] [$id:ident as $alias:ident $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* datafusion::prelude::col(stringify!($id)).alias(stringify!($alias))] [$($($rest)*)?])
    };

    // Munch: function call (with optional comma + rest)
    (@accum [$($result:expr),*] [$func:ident($($args:tt)*) $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* $crate::__parse_expr!($func($($args)*))] [$($($rest)*)?])
    };

    // Munch: identifier (with optional comma + rest)
    (@accum [$($result:expr),*] [$id:ident $(, $($rest:tt)*)?]) => {
        $crate::__parse_exprs!(@accum [$($result,)* datafusion::prelude::col(stringify!($id))] [$($($rest)*)?])
    };

    // Entry point: start with empty accumulator
    ($($tt:tt)*) => {
        $crate::__parse_exprs!(@accum [] [$($tt)*])
    };
}

/// Internal: Parse expression (identifier or function call)
/// Truly recursive - handles any nesting depth
#[doc(hidden)]
#[macro_export]
macro_rules! __parse_expr {
    // Function call: f(x), g(f(x)), etc.
    ($func:ident($($inside:tt)*)) => {
        $func.call($crate::__parse_exprs!($($inside)*))
    };

    // Plain identifier: x
    ($ident:ident) => {
        datafusion::prelude::col(stringify!($ident))
    };
}

/// Internal: Parse predicate (handles &&, ||, and nested calls)
#[doc(hidden)]
#[macro_export]
macro_rules! __parse_predicate {
    // AND: P(...) && Q(...)
    ($p:ident($($arg1:tt)*) && $q:ident($($arg2:tt)*)) => {
        $p.and(
            $crate::__parse_exprs!($($arg1)*),
            $crate::__parse_expr!($q($($arg2)*))
        )
    };

    // OR: P(...) || Q(...)
    ($p:ident($($arg1:tt)*) || $q:ident($($arg2:tt)*)) => {
        $p.or(
            $crate::__parse_exprs!($($arg1)*),
            $crate::__parse_expr!($q($($arg2)*))
        )
    };

    // Simple function call: P(...)
    ($func:ident($($args:tt)*)) => {
        $func.call($crate::__parse_exprs!($($args)*))
    };
}

// ============================================================================
// Public operator macros
// ============================================================================

/// Creates a Filter logical plan node
///
/// # Syntax
/// ```
/// # use rulescript::{filter, schema, functions};
/// # use rulescript::ast::relational::Rel;
/// # let source = Rel::source("table".to_string(), schema!(x: T));
/// # functions! { P(T) -> Bool }
/// filter!(source, P(x));
/// ```
///
/// # Examples
/// ```
/// use rulescript::{filter, schema, functions};
/// use rulescript::ast::relational::Rel;
///
/// let source = Rel::source("table".to_string(), schema!(x: T, y: U));
/// functions! {
///     P(T) -> Bool,
///     Q(T) -> Bool,
///     f(T) -> U,
/// }
///
/// // Simple filter
/// let _plan = filter!(source.clone(), P(x));
///
/// // Nested call
/// let _plan = filter!(source.clone(), P(f(x)));
///
/// // AND
/// let _plan = filter!(source.clone(), P(x) && Q(x));
///
/// // OR
/// let _plan = filter!(source.clone(), P(x) || Q(y));
/// ```
#[macro_export]
macro_rules! filter {
    // Single pattern - input first, then predicate
    // No ambiguity because $input:expr is matched first, then comma, then everything else
    ($input:expr, $($pred:tt)+) => {
        $input.filter($crate::__parse_predicate!($($pred)+)).unwrap()
    };
}

/// Creates a Projection logical plan node
///
/// # Syntax
/// ```
/// # use rulescript::{project, schema, functions};
/// # use rulescript::ast::relational::Rel;
/// # let source = Rel::source("table".to_string(), schema!(x: T));
/// # functions! { f(T) -> U }
/// project!(source, [f(x)]);
/// ```
///
/// # Examples
/// ```
/// use rulescript::{project, schema, functions};
/// use rulescript::ast::relational::Rel;
///
/// let source = Rel::source("table".to_string(), schema!(x: T, y: U));
/// functions! {
///     f(T) -> U,
///     g(U) -> V,
/// }
///
/// // Single column
/// let _plan = project!(source.clone(), [x]);
///
/// // Function call
/// let _plan = project!(source.clone(), [f(x)]);
///
/// // With alias
/// let _plan = project!(source.clone(), [f(x) as result]);
///
/// // Multiple expressions
/// let _plan = project!(source.clone(), [f(x), g(y) as z]);
/// ```
#[macro_export]
macro_rules! project {
    // Input first, then projection list - uses unified __parse_exprs!
    ($input:expr, [$($exprs:tt)*]) => {{
        let expressions = $crate::__parse_exprs!($($exprs)*);
        $input.project(expressions).unwrap()
    }};
}

/// Creates a Join logical plan node
///
/// # Syntax
/// ```
/// # use rulescript::{join, schema, functions};
/// # use rulescript::ast::relational::Rel;
/// # let left = Rel::source("left".to_string(), schema!(l: TL));
/// # let right = Rel::source("right".to_string(), schema!(r: TR));
/// # functions! { pred(TL, TR) -> Bool }
/// join!(left, right, Inner, pred(l, r));
/// ```
///
/// # Examples
/// ```
/// use rulescript::{join, schema, functions};
/// use rulescript::ast::relational::Rel;
///
/// let left = Rel::source("left".to_string(), schema!(l: TL));
/// let right = Rel::source("right".to_string(), schema!(r: TR));
/// functions! {
///     pred(TL, TR) -> Bool,
///     pred2(TL, TR) -> Bool,
/// }
///
/// // Inner join
/// let _plan = join!(left.clone(), right.clone(), Inner, pred(l, r));
///
/// // Left join with AND condition
/// let _plan = join!(left, right, Left, pred(l, r) && pred2(l, r));
/// ```
///
/// # Examples
/// ```ignore
/// join!(left, right, Inner, pred(l, r))
/// join!(left, right, Left, pred(l, r) && pred2(l, r))
/// join!(left, right, Inner, pred(a, f(x), b))  // Mixed args - now supported!
/// ```
#[macro_export]
macro_rules! join {
    // Single pattern - left, right, join type, then condition
    // No ambiguity because inputs are matched first
    ($left:expr, $right:expr, $jtype:ident, $($condition:tt)+) => {
        $left.join(
            $right,
            datafusion::logical_expr::JoinType::$jtype,
            vec![],
            Some($crate::__parse_predicate!($($condition)+))
        ).unwrap()
    };
}

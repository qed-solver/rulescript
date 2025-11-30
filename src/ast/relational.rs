use std::sync::Arc;

use datafusion::{
    common::{JoinConstraint, NullEquality},
    error::Result,
    logical_expr::{
        Aggregate, Distinct, Extension, Filter, Join, JoinType, Limit, LogicalPlan, Projection,
        Sort, SortExpr, Union, build_join_schema,
    },
    prelude::{Expr, lit},
};

use crate::ast::{opaque::Schema, source::Source};

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
// Relational operator macros
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
        $input.filter($crate::pred!($($pred)+)).unwrap()
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
    // Input first, then projection list - uses unified exprs!
    ($input:expr, [$($exprs:tt)*]) => {{
        let expressions = $crate::exprs!($($exprs)*);
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
#[macro_export]
macro_rules! join {
    // Single pattern - left, right, join type, then condition
    // No ambiguity because inputs are matched first
    ($left:expr, $right:expr, $jtype:ident, $($condition:tt)+) => {
        $left.join(
            $right,
            datafusion::logical_expr::JoinType::$jtype,
            vec![],
            Some($crate::pred!($($condition)+))
        ).unwrap()
    };
}

/// Creates an Aggregate logical plan node
///
/// # Syntax
/// ```
/// # use rulescript::{aggregate, schema, functions};
/// # use rulescript::ast::relational::Rel;
/// # let source = Rel::source("table".to_string(), schema!(x: T, y: U));
/// # functions! { agg{U} -> U }
/// aggregate!(source, group: [x], aggs: [agg{y}]);
/// ```
///
/// # Examples
/// ```
/// use rulescript::{aggregate, schema, functions};
/// use rulescript::ast::relational::Rel;
///
/// let source = Rel::source("table".to_string(), schema!(x: T, y: U, z: V));
/// functions! {
///     agg1{U} -> U,
///     agg2{U} -> U,
///     agg3{U} -> W,
/// }
///
/// // Group by with single aggregate
/// let _plan = aggregate!(source.clone(), group: [x], aggs: [agg1{y}]);
///
/// // Multiple aggregates
/// let _plan = aggregate!(source.clone(), group: [x], aggs: [agg1{y}, agg2{z}]);
///
/// // No grouping (global aggregate)
/// let _plan = aggregate!(source.clone(), group: [], aggs: [agg3{y}]);
///
/// // With alias
/// let _plan = aggregate!(source.clone(), group: [x], aggs: [agg1{y} as result]);
/// ```
#[macro_export]
macro_rules! aggregate {
    ($input:expr, group: [$($group:tt)*], aggs: [$($aggs:tt)*]) => {{
        let group_expressions = $crate::exprs!($($group)*);
        let agg_expressions = $crate::exprs!($($aggs)*);
        $input.aggregate(group_expressions, agg_expressions).unwrap()
    }};
}

/// Creates an Extension logical plan node with a user-defined operator
///
/// # Syntax
/// ```
/// # use rulescript::{extend, schema};
/// # use rulescript::ast::extension::UserDefinedLogicalOperator;
/// # use rulescript::ast::opaque::Schema;
/// # use datafusion::logical_expr::LogicalPlan;
/// # // Minimal operator for testing
/// # #[derive(Debug, Clone)]
/// # struct MyOp { schema: Schema, plan: LogicalPlan }
/// # impl MyOp {
/// #     fn new() -> Self {
/// #         let s = schema!(x: T);
/// #         let p = rulescript::ast::relational::Rel::source("t".to_string(), s.clone()).plan;
/// #         Self { schema: s, plan: p.clone() }
/// #     }
/// # }
/// # impl UserDefinedLogicalOperator for MyOp {
/// #     fn operator_name(&self) -> &str { "MyOp" }
/// #     fn inputs(&self) -> Vec<&LogicalPlan> { vec![] }
/// #     fn schema(&self) -> &Schema { &self.schema }
/// #     fn semantics(&self) -> &LogicalPlan { &self.plan }
/// #     fn resolve(&self, _: &rulescript::ast::extension::UserDefinedLogicalPattern,
/// #               _: &LogicalPlan, _: &mut dyn rulescript::matcher::PatternMatcher)
/// #               -> Result<(), rulescript::matcher::RuleError> { Ok(()) }
/// #     fn instantiate(&self, _: &mut dyn rulescript::matcher::PatternMatcher)
/// #                    -> Result<LogicalPlan, rulescript::matcher::RuleError> { Ok(self.plan.clone()) }
/// # }
/// let op = MyOp::new();
/// let _pattern = extend!(op);
/// ```
///
/// # Examples
/// See the `user_defined_left_semi_join` example for a complete demonstration
/// of defining and using custom operators with this macro.
#[macro_export]
macro_rules! extend {
    ($operator:expr) => {
        $crate::ast::relational::Rel {
            plan: datafusion::logical_expr::LogicalPlan::Extension(
                datafusion::logical_expr::Extension {
                    node: std::sync::Arc::new(
                        $crate::ast::extension::UserDefinedLogicalPattern::new(
                            std::sync::Arc::new($operator),
                        ),
                    ),
                },
            ),
        }
    };
}

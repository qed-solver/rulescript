pub mod impls;
#[cfg(test)]
pub mod test;

use std::{
    fmt::{self, Debug, Formatter},
    marker::PhantomData,
};

use datafusion::{
    common::tree_node::{Transformed, TreeNode},
    error::Result as DataFusionResult,
    logical_expr::LogicalPlan,
    optimizer::{ApplyOrder, OptimizerConfig, OptimizerRule},
};

use crate::{
    ast::relational::Rel,
    matcher::{DefaultMatcher, PatternMatcher, RuleError},
};

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

/// Wrapper to use ApplicableRule as DataFusion OptimizerRule
pub struct RuleWrapper<R, M = DefaultMatcher> {
    pub rule: R,
    _phantom: PhantomData<M>,
}

impl<R, M> Debug for RuleWrapper<R, M>
where
    R: Debug,
{
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        f.debug_struct("RuleWrapper")
            .field("rule", &self.rule)
            .finish()
    }
}

impl<R, M> RuleWrapper<R, M> {
    pub fn new(rule: R) -> Self {
        Self {
            rule,
            _phantom: PhantomData,
        }
    }
}

impl<R, M> OptimizerRule for RuleWrapper<R, M>
where
    R: ApplicableRule<M> + Debug,
    M: PatternMatcher + Default,
{
    fn name(&self) -> &str {
        self.rule.name()
    }

    fn apply_order(&self) -> Option<ApplyOrder> {
        None
    }

    fn supports_rewrite(&self) -> bool {
        true
    }

    fn rewrite(
        &self,
        plan: LogicalPlan,
        _config: &dyn OptimizerConfig,
    ) -> DataFusionResult<Transformed<LogicalPlan>> {
        // Try to apply the rule at this node
        match self.rule.try_apply(&plan) {
            Ok(new_plan) => Ok(Transformed::yes(new_plan)),
            Err(_) => {
                // Rule didn't match or failed, try children
                let transformed = plan.transform_down(|node| match self.rule.try_apply(&node) {
                    Ok(new_node) => Ok(Transformed::yes(new_node)),
                    Err(_) => Ok(Transformed::no(node)),
                })?;
                Ok(transformed)
            }
        }
    }
}

/// Declaratively define a rewrite rule
///
/// # Syntax
/// ```
/// use rulescript::{rule, filter};
///
/// rule! {
///     MyFilterRule {
///         schemas: {
///             source: (col: T),
///         },
///         functions: {
///             P(T) -> Bool,
///         },
///         from: filter!(source, P(col)),
///         to: source,
///     }
/// }
/// ```
///
/// # Examples
///
/// Simple filter merge:
/// ```
/// use rulescript::{rule, filter};
///
/// rule! {
///     FilterMergeRule {
///         schemas: {
///             source: (col: T),
///         },
///         functions: {
///             P(T) -> Bool,
///             Q(T) -> Bool,
///         },
///         from: filter!(filter!(source, Q(col)), P(col)),
///         to: filter!(source, P(col) && Q(col)),
///     }
/// }
/// ```
///
/// Join rule with multiple schemas:
/// ```
/// use rulescript::{rule, filter, join};
///
/// rule! {
///     FilterIntoJoin {
///         schemas: {
///             left: (col_l: TL),
///             right: (col_r: TR),
///         },
///         functions: {
///             join_cond(TL, TR) -> Bool,
///             filter_pred(TL, TR) -> Bool,
///         },
///         from: filter!(join!(left, right, Inner, join_cond(col_l, col_r)), filter_pred(col_l, col_r)),
///         to: join!(left, right, Inner, join_cond(col_l, col_r) && filter_pred(col_l, col_r)),
///     }
/// }
/// ```
#[macro_export]
macro_rules! rule {
    (
        $name:ident {
            schemas: {
                $($schema_name:ident: $schema_def:tt),+ $(,)?
            },
            functions: {
                $($func_defs:tt)*
            },
            from: $from_expr:expr,
            to: $to_expr:expr,
        }
    ) => {
        #[derive(Debug)]
        pub struct $name;

        impl $crate::rule::RewriteRule for $name {
            fn from(&self) -> $crate::ast::relational::Rel {
                // Generate schemas as sources
                $(let $schema_name = $crate::ast::relational::Rel::source(
                    stringify!($schema_name).to_string(),
                    $crate::schema!$schema_def
                );)+

                // Generate functions
                $crate::functions! { $($func_defs)* }

                // Build pattern
                $from_expr
            }

            fn to(&self) -> $crate::ast::relational::Rel {
                // Generate schemas as sources (same as from)
                $(let $schema_name = $crate::ast::relational::Rel::source(
                    stringify!($schema_name).to_string(),
                    $crate::schema!$schema_def
                );)+

                // Generate functions (same as from)
                $crate::functions! { $($func_defs)* }

                // Build replacement
                $to_expr
            }

            fn name(&self) -> &str {
                stringify!($name)
            }
        }

        impl $crate::rule::ApplicableRule<$crate::matcher::DefaultMatcher> for $name {}
    };
}

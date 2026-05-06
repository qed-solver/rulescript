//! Wrappers for rules that need additional guards for the interactive demo

use datafusion::{
    common::tree_node::Transformed,
    error::Result as DataFusionResult,
    logical_expr::LogicalPlan,
    optimizer::{OptimizerConfig, OptimizerRule},
};
use rulescript::rule::{
    RuleWrapper,
    impls::{JoinCommuteRule, JoinExtractFilterRule},
};

/// Wrapper for JoinCommute that prevents infinite loops using lexicographic bias
///
/// Without this guard, JoinCommute would always apply since any join can be swapped,
/// creating infinite loops: Join(L, R) → Project(Join(R, L)) → Join(L, R) → ...
///
/// Solution: Only swap when left.display_indent() > right.display_indent() (lexicographically).
/// This creates a stable ordering - the rule only applies in one direction.
#[derive(Debug)]
pub struct BiasedJoinCommuteRule {
    inner: RuleWrapper<JoinCommuteRule>,
}

impl BiasedJoinCommuteRule {
    pub fn new() -> Self {
        Self {
            inner: RuleWrapper::new(JoinCommuteRule),
        }
    }

    /// Only swap if left input is lexicographically greater than right
    fn should_swap(plan: &LogicalPlan) -> bool {
        if let LogicalPlan::Join(join) = plan {
            let left_str = join.left.display_indent().to_string();
            let right_str = join.right.display_indent().to_string();
            left_str > right_str
        } else {
            false
        }
    }
}

impl OptimizerRule for BiasedJoinCommuteRule {
    fn name(&self) -> &str {
        "biased_join_commute"
    }

    fn rewrite(
        &self,
        plan: LogicalPlan,
        config: &dyn OptimizerConfig,
    ) -> DataFusionResult<Transformed<LogicalPlan>> {
        if !Self::should_swap(&plan) {
            return Ok(Transformed::no(plan));
        }

        self.inner.rewrite(plan, config)
    }
}

/// Wrapper for JoinExtractFilter that prevents infinite loops
///
/// Without this guard, JoinExtractFilter and FilterIntoJoin would create
/// infinite loops: Join(cond) → Filter(cond, Join(true)) → Join(cond) → ...
///
/// Solution: Do not apply if the join condition is already TRUE (cartesian join).
#[derive(Debug)]
pub struct BiasedJoinExtractFilterRule {
    inner: RuleWrapper<JoinExtractFilterRule>,
}

impl BiasedJoinExtractFilterRule {
    pub fn new() -> Self {
        Self {
            inner: RuleWrapper::new(JoinExtractFilterRule),
        }
    }

    /// Only extract if condition is not already TRUE
    fn should_extract(plan: &LogicalPlan) -> bool {
        use datafusion::prelude::lit;

        if let LogicalPlan::Join(join) = plan {
            if let Some(filter) = &join.filter {
                // Don't extract if condition is already TRUE (would create infinite loop)
                *filter != lit(true)
            } else {
                false
            }
        } else {
            false
        }
    }
}

impl OptimizerRule for BiasedJoinExtractFilterRule {
    fn name(&self) -> &str {
        "biased_join_extract_filter"
    }

    fn rewrite(
        &self,
        plan: LogicalPlan,
        config: &dyn OptimizerConfig,
    ) -> DataFusionResult<Transformed<LogicalPlan>> {
        if !Self::should_extract(&plan) {
            return Ok(Transformed::no(plan));
        }

        self.inner.rewrite(plan, config)
    }
}

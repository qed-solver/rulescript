//! Wrappers for rules that need additional guards for the interactive demo

use datafusion::{
    common::tree_node::Transformed,
    error::Result as DataFusionResult,
    logical_expr::LogicalPlan,
    optimizer::{OptimizerConfig, OptimizerRule},
};
use rulescript::rule::{RuleWrapper, impls::JoinCommuteRule};

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

    fn apply_order(&self) -> Option<datafusion::optimizer::ApplyOrder> {
        None
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

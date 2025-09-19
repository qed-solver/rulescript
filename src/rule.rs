use datafusion::logical_expr::LogicalPlan;

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

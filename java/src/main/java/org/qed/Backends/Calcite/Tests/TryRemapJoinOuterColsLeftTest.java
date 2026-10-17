package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.TryRemapJoinOuterColsLeft;
import org.apache.calcite.rel.logical.LogicalCorrelate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;

public final class TryRemapJoinOuterColsLeftTest {
    public static void runTest() {
        var rule = new TryRemapJoinOuterColsLeft();
        System.out.println("pattern rewrite:");
        RuleChecks.rewritesPattern(rule);
        System.out.println("concrete predicate rewrite:");
        RuleChecks.rewritesConcretePredicates(rule);
        System.out.println("near misses:");
        RuleChecks.rejectsNearMisses(rule);

        var patternInput = FaithfulPlans.of(rule.before());
        var expected = FaithfulPlans.expected(rule.after());
        RuleChecks.rewrites(rule, patternInput, expected);

        var filter = (LogicalFilter) patternInput;
        var correlate = (LogicalCorrelate) filter.getInput();
        var innerFilter = (LogicalFilter) correlate.getRight();
        var rexBuilder = correlate.getCluster().getRexBuilder();
        var unequal = rexBuilder.makeCall(SqlStdOperatorTable.NOT_EQUALS,
                ((org.apache.calcite.rex.RexCall) innerFilter.getCondition()).getOperands());
        RelNode wrongRight = innerFilter.copy(innerFilter.getTraitSet(), innerFilter.getInput(), unequal);
        var wrongCorrelate = correlate.copy(correlate.getTraitSet(), correlate.getLeft(), wrongRight,
                correlate.getCorrelationId(), correlate.getRequiredColumns(), correlate.getJoinType());
        RuleChecks.doesNotApply(rule, filter.copy(filter.getTraitSet(), wrongCorrelate, filter.getCondition()));
    }

    public static void main(String[] args) {
        System.out.println("Running TryRemapJoinOuterColsLeft test...");
        runTest();
    }
}

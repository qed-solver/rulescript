package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.RewriteLateralSubquery;
import org.qed.RuleBuilder;

public class RewriteLateralSubqueryTest {
    public static void runTest() {
        var rule = new RewriteLateralSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var ruleBuilder = RuleBuilder.create();
        var leftName = RuleChecks.table(ruleBuilder, 2, false);
        var rightName = RuleChecks.table(ruleBuilder, 2, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        RelNode left = builder.scan(leftName).build();
        RelNode right = builder.scan(rightName).build();
        var before = RuleChecks.correlate(builder, left, right, JoinRelType.LEFT,
                refs -> RuleChecks.eq(builder, refs[0], refs[2]));

        // Independently construct the expected ordinary left join over the same scans.
        var expectedRuleBuilder = RuleBuilder.create();
        RuleChecks.table(expectedRuleBuilder, 2, false);
        RuleChecks.table(expectedRuleBuilder, 2, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedRuleBuilder);
        var expectedLeft = expectedBuilder.scan(leftName).build();
        var expectedRight = expectedBuilder.scan(rightName).build();
        var expectedCondition = expectedBuilder.getRexBuilder().makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                expectedBuilder.getRexBuilder().makeInputRef(expectedLeft.getRowType().getFieldList().get(0).getType(), 0),
                expectedBuilder.getRexBuilder().makeInputRef(expectedRight.getRowType().getFieldList().get(0).getType(), 2));
        var expected = LogicalJoin.create(expectedLeft, expectedRight,
                java.util.List.of(), expectedCondition, com.google.common.collect.ImmutableSet.of(), JoinRelType.LEFT);
        RuleChecks.rewrites(rule, before, expected);

        var noCorrelation = LogicalJoin.create(left, right, java.util.List.of(),
                builder.getRexBuilder().makeLiteral(true), com.google.common.collect.ImmutableSet.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, noCorrelation);
    }

    public static void main(String[] args) {
        System.out.println("Running RewriteLateralSubquery test...");
        runTest();
        if (CalciteTester.failures() > 0) System.exit(1);
    }
}

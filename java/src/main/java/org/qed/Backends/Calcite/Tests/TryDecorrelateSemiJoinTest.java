package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalCorrelate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.RelNode;
import org.qed.RRuleInstances.TryDecorrelateSemiJoin;
import org.qed.RuleBuilder;

public class TryDecorrelateSemiJoinTest {
    public static void runTest() {
        var rule = new TryDecorrelateSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltMatch();
        rejectsIndependentJoin();
    }

    private static void rewritesHandBuiltMatch() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, true);
        var rightName = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var input = RuleChecks.correlate(b, left, right, JoinRelType.SEMI,
                args -> RuleChecks.eq(b, args[0], args[1]));

        var expectedRuleBuilder = RuleBuilder.create();
        RuleChecks.table(expectedRuleBuilder, 1, true);
        RuleChecks.table(expectedRuleBuilder, 1, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedRuleBuilder);
        var expectedLeft = expectedBuilder.scan(leftName).build();
        var expectedRight = expectedBuilder.scan(rightName).build();
        var expectedCorrelate = RuleChecks.correlate(expectedBuilder, expectedLeft, expectedRight, JoinRelType.INNER,
                args -> RuleChecks.eq(expectedBuilder, args[0], args[1]));
        var expected = expectedBuilder.push(expectedCorrelate).aggregate(expectedBuilder.groupKey(0)).build();
        RuleChecks.rewrites(new TryDecorrelateSemiJoin(), input, expected);
    }

    private static void rejectsIndependentJoin() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, true);
        var rightName = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var independent = b.push(left).push(right).join(JoinRelType.SEMI,
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(new TryDecorrelateSemiJoin(), independent);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateSemiJoin test...");
        runTest();
    }
}

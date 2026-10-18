package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.DecorrelateProjectSet;
import org.qed.RuleBuilder;

public final class DecorrelateProjectSetTest {
    public static void runTest() {
        var rule = new DecorrelateProjectSet();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var sourceBuilder = RuleBuilder.create();
        var left = table(sourceBuilder);
        var emitted = table(sourceBuilder);
        var sourceRelBuilder = ruleBuilder(sourceBuilder);
        var source = sourceRelBuilder.scan(left).scan(emitted)
                .join(JoinRelType.INNER, sourceRelBuilder.call(
                        org.apache.calcite.sql.fun.SqlStdOperatorTable.LESS_THAN,
                        sourceRelBuilder.field(2, 1, 0), sourceRelBuilder.literal(10)))
                .build();

        var expectedBuilder = RuleBuilder.create();
        var expectedLeft = table(expectedBuilder);
        var expectedEmitted = table(expectedBuilder);
        var expectedRelBuilder = ruleBuilder(expectedBuilder);
        var expected = expectedRelBuilder.scan(expectedLeft).scan(expectedEmitted)
                .filter(expectedRelBuilder.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.LESS_THAN,
                        expectedRelBuilder.field(0), expectedRelBuilder.literal(10)))
                .join(JoinRelType.INNER, expectedRelBuilder.literal(true)).build();
        rewrites(rule, source, expected);

        var nearBuilder = RuleBuilder.create();
        var nearLeft = table(nearBuilder);
        var nearEmitted = table(nearBuilder);
        var nearRelBuilder = ruleBuilder(nearBuilder);
        var nearMiss = nearRelBuilder.scan(nearLeft).scan(nearEmitted)
                .join(JoinRelType.LEFT, nearRelBuilder.call(
                        org.apache.calcite.sql.fun.SqlStdOperatorTable.LESS_THAN,
                        nearRelBuilder.field(2, 1, 0), nearRelBuilder.literal(10)))
                .build();
        doesNotApply(rule, nearMiss);
    }

    private static String table(RuleBuilder builder) {
        return RuleChecks.table(builder, 1, false, false);
    }

    private static RelBuilder ruleBuilder(RuleBuilder builder) {
        return RuleChecks.ruleBuilder(builder);
    }

    private static void rewrites(org.qed.RRule rule, RelNode source, RelNode expected) {
        RuleChecks.rewrites(rule, source, expected);
    }

    private static void doesNotApply(org.qed.RRule rule, RelNode plan) {
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running DecorrelateProjectSet test...");
        runTest();
    }
}

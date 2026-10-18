package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RuleBuilder;

public class AggregateJoinRemoveTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.AggregateJoinRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        RelBuilder b = RuleChecks.ruleBuilder(builder);
        var leftPlan = b.scan(left).build();
        var rightPlan = b.scan(right).build();
        var actualBefore = b.push(leftPlan).push(rightPlan)
                .join(JoinRelType.LEFT, b.literal(true))
                .aggregate(b.groupKey(b.field(0))).build();
        var expected = b.push(leftPlan).aggregate(b.groupKey(b.field(0))).build();
        RuleChecks.rewrites(rule, actualBefore, expected);

        var nearMiss = b.push(leftPlan).push(rightPlan)
                .join(JoinRelType.INNER, b.literal(true))
                .aggregate(b.groupKey(b.field(0))).build();
        RuleChecks.doesNotApply(rule, nearMiss);

        if (CalciteTester.failures() > 0) {
            throw new AssertionError("AggregateJoinRemove test failed: " + CalciteTester.failures());
        }
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateJoinRemove test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelRN;
import org.qed.RRuleInstances.EliminateJoinUnderGroupByLeft;

public final class EliminateJoinUnderGroupByLeftTest {
    public static void runTest() {
        var rule = new EliminateJoinUnderGroupByLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false);
        var rightName = RuleChecks.table(builder, 1, false);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var matchingJoin = builder.scan(leftName).scan(rightName)
                .join(JoinRelType.LEFT, builder.literal(true)).build();
        var matchingAggregate = builder.push(matchingJoin).aggregate(builder.groupKey(0)).build();
        var expectedLeft = builder.scan(leftName).build();
        var expected = builder.push(expectedLeft).aggregate(builder.groupKey(0)).build();
        RuleChecks.rewrites(rule, matchingAggregate, expected);

        var innerJoin = builder.push(left).push(right)
                .join(JoinRelType.INNER, builder.literal(true)).build();
        var nonMatch = builder.push(innerJoin).aggregate(builder.groupKey(0)).build();
        RuleChecks.doesNotApply(rule, nonMatch);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinUnderGroupByLeft test...");
        runTest();
    }
}

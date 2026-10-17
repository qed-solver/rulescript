package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.EliminateJoinUnderGroupByRight;

public final class EliminateJoinUnderGroupByRightTest {
    public static void runTest() {
        var rule = new EliminateJoinUnderGroupByRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var matchingJoin = builder.scan(table).scan(table)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        var matchingAggregate = builder.push(matchingJoin).aggregate(builder.groupKey(1)).build();
        var keptInput = builder.scan(table).build();
        var expected = builder.push(keptInput).aggregate(builder.groupKey(0)).build();
        RuleChecks.rewrites(rule, matchingAggregate, expected);

        var nonMatchJoin = builder.scan(table).scan(table)
                .join(JoinRelType.LEFT, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        var nonMatch = builder.push(nonMatchJoin).aggregate(builder.groupKey(1)).build();
        RuleChecks.doesNotApply(rule, nonMatch);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinUnderGroupByRight test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.EliminateJoinUnderGroupByRight;
import org.qed.RuleBuilder;

public class EliminateJoinUnderGroupByRightTest {

    public static void runTest() {
        var rule = new EliminateJoinUnderGroupByRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        selfJoinOnKey(rule);
    }

    private static void selfJoinOnKey(EliminateJoinUnderGroupByRight rule) {
        var builder = RuleBuilder.create();
        var t = RuleChecks.table(builder, 1, false, false);
        var before = builder.scan(t).scan(t)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .aggregate(builder.groupKey(1))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(t).aggregate(plain.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        var leftJoin = builder.scan(t).scan(t)
                .join(JoinRelType.LEFT, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .aggregate(builder.groupKey(1))
                .build();
        RuleChecks.doesNotApply(rule, leftJoin);

        var leftKey = builder.scan(t).scan(t)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .aggregate(builder.groupKey(0))
                .build();
        RuleChecks.doesNotApply(rule, leftKey);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinUnderGroupByRight test...");
        runTest();
    }
}

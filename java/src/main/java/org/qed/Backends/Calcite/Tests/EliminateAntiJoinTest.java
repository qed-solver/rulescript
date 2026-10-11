package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.EliminateAntiJoin;
import org.qed.RuleBuilder;

public class EliminateAntiJoinTest {

    public static void runTest() {
        var rule = new EliminateAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        emptyRightSide(rule);
    }

    private static void emptyRightSide(EliminateAntiJoin rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var before = builder.scan(l).scan(r).empty()
                .join(JoinRelType.ANTI, builder.literal(true)).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).build();
        RuleChecks.rewrites(rule, before, after);

        var nonEmpty = builder.scan(l).scan(r)
                .join(JoinRelType.ANTI, builder.literal(true)).build();
        RuleChecks.doesNotApply(rule, nonEmpty);

        var leftJoin = builder.scan(l).scan(r).empty()
                .join(JoinRelType.LEFT, builder.literal(true)).build();
        RuleChecks.doesNotApply(rule, leftJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAntiJoin test...");
        runTest();
    }
}

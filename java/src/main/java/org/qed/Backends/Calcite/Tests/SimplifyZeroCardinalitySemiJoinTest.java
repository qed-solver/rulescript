package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.SimplifyZeroCardinalitySemiJoin;
import org.qed.RuleBuilder;

public class SimplifyZeroCardinalitySemiJoinTest {

    public static void runTest() {
        var rule = new SimplifyZeroCardinalitySemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        emptyRightSide(rule);
    }

    private static void emptyRightSide(SimplifyZeroCardinalitySemiJoin rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var before = builder.scan(l).scan(r).empty()
                .join(JoinRelType.SEMI, builder.literal(true)).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).empty().build();
        RuleChecks.rewrites(rule, before, after);

        var nonEmpty = builder.scan(l).scan(r)
                .join(JoinRelType.SEMI, builder.literal(true)).build();
        RuleChecks.doesNotApply(rule, nonEmpty);

        var anti = builder.scan(l).scan(r).empty()
                .join(JoinRelType.ANTI, builder.literal(true)).build();
        RuleChecks.doesNotApply(rule, anti);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyZeroCardinalitySemiJoin test...");
        runTest();
    }
}

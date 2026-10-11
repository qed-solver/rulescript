package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.RightAssociateJoinsLeft;
import org.qed.RuleBuilder;

public class RightAssociateJoinsLeftTest {

    public static void runTest() {
        var rule = new RightAssociateJoinsLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RightAssociateJoinsLeft rule) {
        var builder = RuleBuilder.create();
        var a = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.table(builder, 1, false);
        var c = RuleChecks.table(builder, 1, false);
        var pab = builder.genericPredicateOp("p_ab", true);
        var pabc = builder.genericPredicateOp("p_abc", true);
        var before = builder.scan(a).scan(b).scan(c)
                .join(JoinRelType.INNER, builder.literal(true))
                .join(JoinRelType.INNER, builder.and(
                        builder.call(pab, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(pabc, builder.field(2, 0, 0), builder.field(2, 1, 0), builder.field(2, 1, 1))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(a).scan(b)
                .join(JoinRelType.INNER, plain.call(pab, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .scan(c)
                .join(JoinRelType.INNER, plain.call(pabc, plain.field(2, 0, 0), plain.field(2, 0, 1), plain.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(a).scan(b).scan(c)
                .join(JoinRelType.INNER, builder.literal(true))
                .join(JoinRelType.INNER, builder.call(pab, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RightAssociateJoinsLeft test...");
        runTest();
    }
}

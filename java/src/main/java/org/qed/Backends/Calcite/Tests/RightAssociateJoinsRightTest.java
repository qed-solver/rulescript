package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.RightAssociateJoinsRight;
import org.qed.RuleBuilder;

public class RightAssociateJoinsRightTest {

    public static void runTest() {
        var rule = new RightAssociateJoinsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RightAssociateJoinsRight rule) {
        var builder = RuleBuilder.create();
        var a = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.table(builder, 1, false);
        var c = RuleChecks.table(builder, 1, false);
        var pac = builder.genericPredicateOp("p_ac", true);
        var pabc = builder.genericPredicateOp("p_abc", true);
        var before = builder.scan(a).scan(b).scan(c)
                .join(JoinRelType.INNER, builder.literal(true))
                .join(JoinRelType.INNER, builder.and(
                        builder.call(pac, builder.field(2, 0, 0), builder.field(2, 1, 1)),
                        builder.call(pabc, builder.field(2, 0, 0), builder.field(2, 1, 0), builder.field(2, 1, 1))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(a).scan(c)
                .join(JoinRelType.INNER, plain.call(pac, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .scan(b)
                .join(JoinRelType.INNER, plain.call(pabc, plain.field(2, 0, 0), plain.field(2, 1, 0), plain.field(2, 0, 1)))
                .project(plain.field(0), plain.field(2), plain.field(1))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(a).scan(b).scan(c)
                .join(JoinRelType.INNER, builder.literal(true))
                .join(JoinRelType.INNER, builder.call(pac, builder.field(2, 0, 0), builder.field(2, 1, 1)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RightAssociateJoinsRight test...");
        runTest();
    }
}

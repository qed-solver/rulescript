package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.LeftAssociateJoinsLeft;
import org.qed.RuleBuilder;

public class LeftAssociateJoinsLeftTest {

    public static void runTest() {
        var rule = new LeftAssociateJoinsLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(LeftAssociateJoinsLeft rule) {
        var builder = RuleBuilder.create();
        var a = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.table(builder, 1, false);
        var c = RuleChecks.table(builder, 1, false);
        var bound = builder.genericPredicateOp("bound", true);
        var unbound = builder.genericPredicateOp("unbound", true);
        var before = builder.scan(a).scan(b)
                .join(JoinRelType.INNER, builder.literal(true))
                .scan(c)
                .join(JoinRelType.INNER, builder.and(
                        builder.call(bound, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(unbound, builder.field(2, 0, 1), builder.field(2, 1, 0))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(b)
                .scan(a).scan(c)
                .join(JoinRelType.INNER, plain.call(bound, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .join(JoinRelType.INNER, plain.call(unbound, plain.field(2, 0, 0), plain.field(2, 1, 1)))
                .project(plain.field(1, 0, 1), plain.field(1, 0, 0), plain.field(1, 0, 2))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(a).scan(b)
                .join(JoinRelType.INNER, builder.literal(true))
                .scan(c)
                .join(JoinRelType.INNER, builder.call(bound, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running LeftAssociateJoinsLeft test...");
        runTest();
    }
}

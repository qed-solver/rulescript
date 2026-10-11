package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.EliminateSemiJoin;
import org.qed.RuleBuilder;

public class EliminateSemiJoinTest {

    public static void runTest() {
        var rule = new EliminateSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        selfSemiJoinTrue(rule);
    }

    private static void selfSemiJoinTrue(EliminateSemiJoin rule) {
        var builder = RuleBuilder.create();
        var t = RuleChecks.table(builder, 1, false);
        var before = builder.scan(t).scan(t)
                .join(JoinRelType.SEMI, builder.literal(true)).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(t).build();
        RuleChecks.rewrites(rule, before, after);

        var inner = builder.scan(t).scan(t)
                .join(JoinRelType.INNER, builder.literal(true)).build();
        RuleChecks.doesNotApply(rule, inner);

        var filtered = builder.scan(t).scan(t)
                .join(JoinRelType.SEMI, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, filtered);

        var other = RuleChecks.table(builder, 1, false);
        var differentInputs = builder.scan(t).scan(other)
                .join(JoinRelType.SEMI, builder.literal(true)).build();
        RuleChecks.doesNotApply(rule, differentInputs);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateSemiJoin test...");
        runTest();
    }
}

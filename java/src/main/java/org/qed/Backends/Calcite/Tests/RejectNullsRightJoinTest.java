package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.RejectNullsRightJoin;
import org.qed.RuleBuilder;

public class RejectNullsRightJoinTest {

    public static void runTest() {
        var rule = new RejectNullsRightJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RejectNullsRightJoin rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var before = builder.scan(l).scan(r)
                .join(JoinRelType.FULL, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.isNotNull(builder.field(0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.LEFT, RuleChecks.eq(plain, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .filter(plain.isNotNull(plain.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(l).scan(r)
                .join(JoinRelType.FULL, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.isNotNull(builder.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RejectNullsRightJoin test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.TryDecorrelateInnerLeftJoin;
import org.qed.RuleBuilder;

public class TryDecorrelateInnerLeftJoinTest {

    public static void runTest() {
        var rule = new TryDecorrelateInnerLeftJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateInnerLeftJoin rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var il = RuleChecks.table(builder, 1, false);
        var ir = RuleChecks.table(builder, 1, false);
        var on = builder.genericPredicateOp("on", true);
        var innerOn = builder.genericPredicateOp("innerOn", true);
        var before = builder.scan(l)
                .scan(il).scan(ir)
                .join(JoinRelType.LEFT, builder.call(innerOn, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .join(JoinRelType.INNER, builder.call(on, builder.field(2, 0, 0), builder.field(2, 1, 1)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l)
                .scan(il)
                .join(JoinRelType.INNER, plain.call(on, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .scan(ir)
                .join(JoinRelType.LEFT, plain.call(innerOn, plain.field(2, 0, 1), plain.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(l)
                .scan(il).scan(ir)
                .join(JoinRelType.INNER, builder.call(innerOn, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .join(JoinRelType.INNER, builder.call(on, builder.field(2, 0, 0), builder.field(2, 1, 1)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateInnerLeftJoin test...");
        runTest();
    }
}

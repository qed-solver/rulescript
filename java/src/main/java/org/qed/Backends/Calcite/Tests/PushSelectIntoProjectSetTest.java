package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.PushSelectIntoProjectSet;
import org.qed.RuleBuilder;

public class PushSelectIntoProjectSetTest {

    public static void runTest() {
        var rule = new PushSelectIntoProjectSet();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoProjectSet rule) {
        var builder = RuleBuilder.create();
        var input = RuleChecks.table(builder, 1, false);
        var emitted = RuleChecks.table(builder, 1, false);
        var m = builder.genericPredicateOp("M", true);
        var p = builder.genericPredicateOp("P", true);
        var q = builder.genericPredicateOp("Q", true);
        var before = builder.scan(input).scan(emitted)
                .join(JoinRelType.INNER, builder.call(m, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.and(
                        builder.call(p, builder.field(0)),
                        builder.call(q, builder.field(1))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(input)
                .filter(plain.call(p, plain.field(0)))
                .scan(emitted)
                .join(JoinRelType.INNER, plain.call(m, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .filter(plain.call(q, plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var leftJoin = builder.scan(input).scan(emitted)
                .join(JoinRelType.LEFT, builder.call(m, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.and(
                        builder.call(p, builder.field(0)),
                        builder.call(q, builder.field(1))))
                .build();
        RuleChecks.doesNotApply(rule, leftJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoProjectSet test...");
        runTest();
    }
}

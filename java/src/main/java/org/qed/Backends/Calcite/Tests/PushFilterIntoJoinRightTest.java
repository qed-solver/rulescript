package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.PushFilterIntoJoinRight;
import org.qed.RuleBuilder;

public class PushFilterIntoJoinRightTest {

    public static void runTest() {
        var rule = new PushFilterIntoJoinRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushFilterIntoJoinRight rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var f = base.genericPredicateOp("right_bound_conj", true);
        var g = base.genericPredicateOp("unbound_conj", true);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.and(b.call(f, b.field(2, 1, 0)),
                        b.call(g, b.field(2, 0, 0), b.field(2, 1, 0))))
                .build();
        var after = b.scan(l).scan(r).filter(b.call(f, b.field(0)))
                .join(JoinRelType.INNER, b.call(g, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.LEFT, b.and(b.call(f, b.field(2, 1, 0)),
                        b.call(g, b.field(2, 0, 0), b.field(2, 1, 0))))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.call(f, b.field(2, 1, 0)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PushFilterIntoJoinRight test...");
        runTest();
    }
}

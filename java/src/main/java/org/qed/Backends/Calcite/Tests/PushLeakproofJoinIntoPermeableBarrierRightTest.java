package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.PushLeakproofJoinIntoPermeableBarrierRight;
import org.qed.RuleBuilder;

import java.util.List;

public class PushLeakproofJoinIntoPermeableBarrierRightTest {

    public static void runTest() {
        var rule = new PushLeakproofJoinIntoPermeableBarrierRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushLeakproofJoinIntoPermeableBarrierRight rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var r = RuleChecks.table(base, 2, false);
        var c = base.genericPredicateOp("leakproof_conj", true);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(l).scan(r)
                .project(List.of(b.field(0), b.field(1)), List.of(), true)
                .join(JoinRelType.INNER, b.call(c, b.field(2, 0, 0), b.field(2, 0, 1),
                        b.field(2, 1, 0), b.field(2, 1, 1)))
                .build();
        var plain = RuleChecks.ruleBuilder(base);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.INNER, plain.call(c, plain.field(2, 0, 0), plain.field(2, 0, 1),
                        plain.field(2, 1, 0), plain.field(2, 1, 1)))
                .project(List.of(plain.field(0), plain.field(1), plain.field(2), plain.field(3)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.call(c, b.field(2, 0, 0), b.field(2, 0, 1),
                        b.field(2, 1, 0), b.field(2, 1, 1)))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .project(List.of(b.field(0), b.field(1)), List.of(), true)
                .join(JoinRelType.LEFT, b.call(c, b.field(2, 0, 0), b.field(2, 0, 1),
                        b.field(2, 1, 0), b.field(2, 1, 1)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PushLeakproofJoinIntoPermeableBarrierRight test...");
        runTest();
    }
}

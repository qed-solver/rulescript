package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.MapFilterIntoJoinRight;
import org.qed.RuleBuilder;

public class MapFilterIntoJoinRightTest {

    public static void runTest() {
        var rule = new MapFilterIntoJoinRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static RexNode equal(RelBuilder b) {
        return RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0));
    }

    private static void ordinaryTables(MapFilterIntoJoinRight rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false, false);
        var r = RuleChecks.table(base, 1, false, false);
        var f = base.genericPredicateOp("mapped_conj", true);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.and(equal(b), b.call(f, b.field(2, 0, 0))))
                .build();
        var after = b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.and(equal(b), b.call(f, b.field(2, 1, 0))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.LEFT, b.and(equal(b), b.call(f, b.field(2, 0, 0))))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.call(f, b.field(2, 0, 0)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running MapFilterIntoJoinRight test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.DetectJoinContradiction;
import org.qed.RuleBuilder;

public class DetectJoinContradictionTest {

    public static void runTest() {
        var rule = new DetectJoinContradiction();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(DetectJoinContradiction rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);

        b.scan(l).scan(r);
        var before = b.join(JoinRelType.INNER, b.and(b.literal(false),
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))).build();

        b.scan(l).scan(r);
        var after = b.join(JoinRelType.INNER, b.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        b.scan(l).scan(r);
        var leftJoin = b.join(JoinRelType.LEFT, b.and(b.literal(false),
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))).build();
        RuleChecks.doesNotApply(rule, leftJoin);

        b.scan(l).scan(r);
        var noContradiction = b.join(JoinRelType.INNER, b.and(
                RuleChecks.gt(b, b.field(2, 0, 0), 5),
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))).build();
        RuleChecks.doesNotApply(rule, noContradiction);

        b.scan(l).scan(r);
        var soleFalse = b.join(JoinRelType.INNER, b.literal(false)).build();
        RuleChecks.doesNotApply(rule, soleFalse);
    }

    public static void main(String[] args) {
        System.out.println("Running DetectJoinContradiction test...");
        runTest();
    }
}

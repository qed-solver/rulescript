package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushFilterIntoSetOp;
import org.qed.RuleBuilder;

public class PushFilterIntoSetOpTest {

    public static void runTest() {
        var rule = new PushFilterIntoSetOp();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushFilterIntoSetOp rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(l).scan(r).intersect(false, 2).filter(RuleChecks.gt(b, b.field(0), 5)).build();
        var after = b.scan(l).filter(RuleChecks.gt(b, b.field(0), 5))
                .scan(r).filter(RuleChecks.gt(b, b.field(0), 5))
                .intersect(false, 2).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).intersect(true, 2)
                .filter(RuleChecks.gt(b, b.field(0), 5)).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).union(false, 2)
                .filter(RuleChecks.gt(b, b.field(0), 5)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running PushFilterIntoSetOp test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.AggregateExpandWithinDistinct;
import org.qed.RuleBuilder;

public class AggregateExpandWithinDistinctTest {

    public static void runTest() {
        var rule = new AggregateExpandWithinDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateExpandWithinDistinct rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).aggregate(b.groupKey(), b.count(true, "cnt", b.field(0))).build();
        b.scan(t).aggregate(b.groupKey(0));
        var after = b.aggregate(b.groupKey(), b.count(false, "cnt", b.field(0))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).aggregate(b.groupKey(), b.count(false, "cnt", b.field(0))).build());
        RuleChecks.doesNotApply(rule, b.scan(t).aggregate(b.groupKey(0), b.count(true, "cnt", b.field(0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateExpandWithinDistinct test...");
        runTest();
    }
}

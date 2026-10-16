package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.TryRemapSelectOuterCols;
import org.qed.RuleBuilder;

public class TryRemapSelectOuterColsTest {

    public static void runTest() {
        var rule = new TryRemapSelectOuterCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryRemapSelectOuterCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 3, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).filter(RuleChecks.gt(b, b.field(0), 0)).filter(RuleChecks.eq(b, b.field(0), b.field(1))).build();
        var after = b.scan(t).filter(RuleChecks.gt(b, b.field(1), 0)).filter(RuleChecks.eq(b, b.field(0), b.field(1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).filter(RuleChecks.gt(b, b.field(0), 0))
                .filter(RuleChecks.eq(b, b.field(0), b.field(2))).build());
        RuleChecks.doesNotApply(rule, b.scan(t).filter(RuleChecks.eq(b, b.field(0), b.field(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running TryRemapSelectOuterCols test...");
        runTest();
    }
}

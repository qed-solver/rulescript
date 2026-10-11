package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateSetLeft;
import org.qed.RuleBuilder;

public class EliminateSetLeftTest {

    public static void runTest() {
        var rule = new EliminateSetLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateSetLeft rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 3, false);
        var tb = RuleChecks.table(base, 3, false);
        var b = RuleChecks.ruleBuilder(base);
        var left = b.scan(ta).project(b.field(2), b.field(0), b.field(1)).build();
        var emptyRight = b.scan(tb).project(b.field(2), b.field(0), b.field(1)).empty().build();
        var before = b.push(left).push(emptyRight).union(true, 2).build();
        var after = b.scan(ta).project(b.field(2), b.field(0), b.field(1)).build();
        RuleChecks.rewrites(rule, before, after);

        var distinctUnion = b.push(left).push(emptyRight).union(false, 2).build();
        RuleChecks.doesNotApply(rule, distinctUnion);
        var nonEmptyRight = b.push(left).scan(tb).union(true, 2).build();
        RuleChecks.doesNotApply(rule, nonEmptyRight);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateSetLeft test...");
        runTest();
    }
}

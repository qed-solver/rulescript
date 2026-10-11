package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateDistinctSetLeft;
import org.qed.RuleBuilder;

public class EliminateDistinctSetLeftTest {

    public static void runTest() {
        var rule = new EliminateDistinctSetLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateDistinctSetLeft rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 3, false);
        var tb = RuleChecks.table(base, 3, false);
        var b = RuleChecks.ruleBuilder(base);
        var left = b.scan(ta).project(b.field(2), b.field(0), b.field(1)).build();
        var emptyRight = b.scan(ta).project(b.field(2), b.field(0), b.field(1)).empty().build();
        var before = b.push(left).push(emptyRight).union(false, 2).build();
        var after = b.scan(ta).project(b.field(2), b.field(0), b.field(1))
                .aggregate(b.groupKey(0, 1, 2)).build();
        RuleChecks.rewrites(rule, before, after);

        var allUnion = b.push(left).push(emptyRight).union(true, 2).build();
        RuleChecks.doesNotApply(rule, allUnion);
        var nonEmptyRight = b.push(left).scan(tb).union(false, 2).build();
        RuleChecks.doesNotApply(rule, nonEmptyRight);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateDistinctSetLeft test...");
        runTest();
    }
}

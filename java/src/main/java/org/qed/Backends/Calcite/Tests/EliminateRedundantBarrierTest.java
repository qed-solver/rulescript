package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateRedundantBarrier;
import org.qed.RuleBuilder;

import java.util.List;

public class EliminateRedundantBarrierTest {

    public static void runTest() {
        var rule = new EliminateRedundantBarrier();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateRedundantBarrier rule) {
        var base = RuleBuilder.create();
        var one = RuleChecks.table(base, 1, false);
        var two = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var first = List.of(b.field(0));
        var before = b.scan(one).project(first, List.of(), true).project(first, List.of(), true).build();
        var after = b.scan(one).project(first, List.of(), false).build();
        RuleChecks.rewrites(rule, before, after);

        var shifted = b.scan(two).project(List.of(b.field(1)), List.of(), true)
                .project(List.of(b.field(0)), List.of(), true).build();
        RuleChecks.doesNotApply(rule, shifted);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateRedundantBarrier test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateGroupByProject;
import org.qed.RuleBuilder;

public class EliminateGroupByProjectTest {

    public static void runTest() {
        var rule = new EliminateGroupByProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateGroupByProject rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).project(b.field(0)).aggregate(b.groupKey(0)).build();
        var after = b.scan(t).aggregate(b.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).project(b.field(1)).aggregate(b.groupKey(0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateGroupByProject test...");
        runTest();
    }
}

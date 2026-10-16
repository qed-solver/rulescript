package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ColumnPruning;

public class ColumnPruningTest {

    public static void runTest() {
        var rule = new ColumnPruning();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ColumnPruning test...");
        runTest();
    }
}

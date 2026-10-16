package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.CleanupDynamicPruningFilters;

public class CleanupDynamicPruningFiltersTest {

    public static void runTest() {
        var rule = new CleanupDynamicPruningFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running CleanupDynamicPruningFilters test...");
        runTest();
    }
}

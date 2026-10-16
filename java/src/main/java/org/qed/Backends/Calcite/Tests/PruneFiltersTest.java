package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PruneFilters;

public class PruneFiltersTest {

    public static void runTest() {
        var rule = new PruneFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneFilters test...");
        runTest();
    }
}

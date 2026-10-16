package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.CombineTypedFilters;

public class CombineTypedFiltersTest {

    public static void runTest() {
        var rule = new CombineTypedFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running CombineTypedFilters test...");
        runTest();
    }
}

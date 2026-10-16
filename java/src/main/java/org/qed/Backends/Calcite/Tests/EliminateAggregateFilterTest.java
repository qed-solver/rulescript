package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateAggregateFilter;

public class EliminateAggregateFilterTest {

    public static void runTest() {
        var rule = new EliminateAggregateFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAggregateFilter test...");
        runTest();
    }
}

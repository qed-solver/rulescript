package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.RemoveRedundantAggregates;

public class RemoveRedundantAggregatesTest {

    public static void runTest() {
        var rule = new RemoveRedundantAggregates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveRedundantAggregates test...");
        runTest();
    }
}

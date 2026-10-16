package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PullupCorrelatedPredicates;

public class PullupCorrelatedPredicatesTest {

    public static void runTest() {
        var rule = new PullupCorrelatedPredicates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PullupCorrelatedPredicates test...");
        runTest();
    }
}

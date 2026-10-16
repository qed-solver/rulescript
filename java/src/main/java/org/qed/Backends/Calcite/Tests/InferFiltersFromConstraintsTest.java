package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.InferFiltersFromConstraints;

public class InferFiltersFromConstraintsTest {

    public static void runTest() {
        var rule = new InferFiltersFromConstraints();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running InferFiltersFromConstraints test...");
        runTest();
    }
}

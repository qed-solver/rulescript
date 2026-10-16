package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.SimplifyBinaryComparison;

public class SimplifyBinaryComparisonTest {

    public static void runTest() {
        var rule = new SimplifyBinaryComparison();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyBinaryComparison test...");
        runTest();
    }
}

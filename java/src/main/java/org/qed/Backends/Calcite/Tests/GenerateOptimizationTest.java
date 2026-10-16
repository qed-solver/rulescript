package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.GenerateOptimization;

public class GenerateOptimizationTest {

    public static void runTest() {
        var rule = new GenerateOptimization();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running GenerateOptimization test...");
        runTest();
    }
}

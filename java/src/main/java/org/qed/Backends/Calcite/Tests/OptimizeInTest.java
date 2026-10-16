package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.OptimizeIn;

public class OptimizeInTest {

    public static void runTest() {
        var rule = new OptimizeIn();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running OptimizeIn test...");
        runTest();
    }
}

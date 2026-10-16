package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.TransposeWindow;

public class TransposeWindowTest {

    public static void runTest() {
        var rule = new TransposeWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running TransposeWindow test...");
        runTest();
    }
}

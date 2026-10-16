package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.BooleanSimplification;

public class BooleanSimplificationTest {

    public static void runTest() {
        var rule = new BooleanSimplification();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running BooleanSimplification test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.FoldablePropagation;

public class FoldablePropagationTest {

    public static void runTest() {
        var rule = new FoldablePropagation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldablePropagation test...");
        runTest();
    }
}

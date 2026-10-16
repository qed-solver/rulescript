package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.RemoveNoopUnion;

public class RemoveNoopUnionTest {

    public static void runTest() {
        var rule = new RemoveNoopUnion();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveNoopUnion test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.RemoveNoopOperators;

public class RemoveNoopOperatorsTest {

    public static void runTest() {
        var rule = new RemoveNoopOperators();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveNoopOperators test...");
        runTest();
    }
}

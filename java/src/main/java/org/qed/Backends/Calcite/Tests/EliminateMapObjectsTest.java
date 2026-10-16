package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateMapObjects;

public class EliminateMapObjectsTest {

    public static void runTest() {
        var rule = new EliminateMapObjects();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateMapObjects test...");
        runTest();
    }
}

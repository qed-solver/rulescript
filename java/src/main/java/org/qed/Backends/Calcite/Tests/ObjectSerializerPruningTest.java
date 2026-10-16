package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ObjectSerializerPruning;

public class ObjectSerializerPruningTest {

    public static void runTest() {
        var rule = new ObjectSerializerPruning();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ObjectSerializerPruning test...");
        runTest();
    }
}

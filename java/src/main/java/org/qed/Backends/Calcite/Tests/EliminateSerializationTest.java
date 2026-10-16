package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateSerialization;

public class EliminateSerializationTest {

    public static void runTest() {
        var rule = new EliminateSerialization();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateSerialization test...");
        runTest();
    }
}

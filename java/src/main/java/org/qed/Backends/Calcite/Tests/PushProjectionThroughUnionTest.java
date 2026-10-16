package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushProjectionThroughUnion;

public class PushProjectionThroughUnionTest {

    public static void runTest() {
        var rule = new PushProjectionThroughUnion();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PushProjectionThroughUnion test...");
        runTest();
    }
}

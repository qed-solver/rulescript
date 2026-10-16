package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushDownJoinThroughUnion;

public class PushDownJoinThroughUnionTest {

    public static void runTest() {
        var rule = new PushDownJoinThroughUnion();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PushDownJoinThroughUnion test...");
        runTest();
    }
}

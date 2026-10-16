package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushPredicateThroughNonJoin;

public class PushPredicateThroughNonJoinTest {

    public static void runTest() {
        var rule = new PushPredicateThroughNonJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PushPredicateThroughNonJoin test...");
        runTest();
    }
}

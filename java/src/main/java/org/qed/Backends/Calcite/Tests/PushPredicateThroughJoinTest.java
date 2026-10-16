package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushPredicateThroughJoin;

public class PushPredicateThroughJoinTest {

    public static void runTest() {
        var rule = new PushPredicateThroughJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PushPredicateThroughJoin test...");
        runTest();
    }
}

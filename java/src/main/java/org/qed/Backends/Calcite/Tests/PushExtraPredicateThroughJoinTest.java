package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushExtraPredicateThroughJoin;

public class PushExtraPredicateThroughJoinTest {

    public static void runTest() {
        var rule = new PushExtraPredicateThroughJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PushExtraPredicateThroughJoin test...");
        runTest();
    }
}

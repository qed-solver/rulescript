package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushDownLeftSemiAntiJoin;

public class PushDownLeftSemiAntiJoinTest {

    public static void runTest() {
        var rule = new PushDownLeftSemiAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PushDownLeftSemiAntiJoin test...");
        runTest();
    }
}

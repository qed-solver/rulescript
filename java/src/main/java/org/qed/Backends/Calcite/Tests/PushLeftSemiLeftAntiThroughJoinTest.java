package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushLeftSemiLeftAntiThroughJoin;

public class PushLeftSemiLeftAntiThroughJoinTest {

    public static void runTest() {
        var rule = new PushLeftSemiLeftAntiThroughJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PushLeftSemiLeftAntiThroughJoin test...");
        runTest();
    }
}

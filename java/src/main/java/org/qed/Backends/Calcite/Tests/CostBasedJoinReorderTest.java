package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.CostBasedJoinReorder;

public class CostBasedJoinReorderTest {

    public static void runTest() {
        var rule = new CostBasedJoinReorder();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running CostBasedJoinReorder test...");
        runTest();
    }
}

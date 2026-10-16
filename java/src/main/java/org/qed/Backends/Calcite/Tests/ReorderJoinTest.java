package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ReorderJoin;

public class ReorderJoinTest {

    public static void runTest() {
        var rule = new ReorderJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ReorderJoin test...");
        runTest();
    }
}

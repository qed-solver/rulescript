package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateOuterJoin;

public class EliminateOuterJoinTest {

    public static void runTest() {
        var rule = new EliminateOuterJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateOuterJoin test...");
        runTest();
    }
}

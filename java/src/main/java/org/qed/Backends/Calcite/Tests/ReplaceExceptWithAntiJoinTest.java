package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ReplaceExceptWithAntiJoin;

public class ReplaceExceptWithAntiJoinTest {

    public static void runTest() {
        var rule = new ReplaceExceptWithAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceExceptWithAntiJoin test...");
        runTest();
    }
}

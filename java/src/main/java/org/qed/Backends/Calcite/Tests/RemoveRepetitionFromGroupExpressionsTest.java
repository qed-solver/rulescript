package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.RemoveRepetitionFromGroupExpressions;

public class RemoveRepetitionFromGroupExpressionsTest {

    public static void runTest() {
        var rule = new RemoveRepetitionFromGroupExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveRepetitionFromGroupExpressions test...");
        runTest();
    }
}

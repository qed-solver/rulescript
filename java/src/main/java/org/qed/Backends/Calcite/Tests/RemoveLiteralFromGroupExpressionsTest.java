package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.RemoveLiteralFromGroupExpressions;

public class RemoveLiteralFromGroupExpressionsTest {

    public static void runTest() {
        var rule = new RemoveLiteralFromGroupExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveLiteralFromGroupExpressions test...");
        runTest();
    }
}

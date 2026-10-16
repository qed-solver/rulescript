package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PullOutNestedDataOuterRefExpressions;

public class PullOutNestedDataOuterRefExpressionsTest {

    public static void runTest() {
        var rule = new PullOutNestedDataOuterRefExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PullOutNestedDataOuterRefExpressions test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ReplaceNullWithFalseInPredicate;

public class ReplaceNullWithFalseInPredicateTest {

    public static void runTest() {
        var rule = new ReplaceNullWithFalseInPredicate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceNullWithFalseInPredicate test...");
        runTest();
    }
}

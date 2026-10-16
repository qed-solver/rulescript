package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.RewritePredicateSubquery;

public class RewritePredicateSubqueryTest {

    public static void runTest() {
        var rule = new RewritePredicateSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RewritePredicateSubquery test...");
        runTest();
    }
}

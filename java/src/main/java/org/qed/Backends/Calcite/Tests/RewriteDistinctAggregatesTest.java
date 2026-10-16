package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.RewriteDistinctAggregates;

public class RewriteDistinctAggregatesTest {

    public static void runTest() {
        var rule = new RewriteDistinctAggregates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RewriteDistinctAggregates test...");
        runTest();
    }
}

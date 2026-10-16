package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.RewriteLateralSubquery;

public class RewriteLateralSubqueryTest {

    public static void runTest() {
        var rule = new RewriteLateralSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RewriteLateralSubquery test...");
        runTest();
    }
}

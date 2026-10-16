package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.RewriteCorrelatedScalarSubquery;

public class RewriteCorrelatedScalarSubqueryTest {

    public static void runTest() {
        var rule = new RewriteCorrelatedScalarSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RewriteCorrelatedScalarSubquery test...");
        runTest();
    }
}

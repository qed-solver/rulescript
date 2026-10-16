package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.OptimizeOneRowRelationSubquery;

public class OptimizeOneRowRelationSubqueryTest {

    public static void runTest() {
        var rule = new OptimizeOneRowRelationSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running OptimizeOneRowRelationSubquery test...");
        runTest();
    }
}

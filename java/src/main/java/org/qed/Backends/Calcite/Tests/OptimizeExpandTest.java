package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.OptimizeExpand;

public class OptimizeExpandTest {

    public static void runTest() {
        var rule = new OptimizeExpand();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running OptimizeExpand test...");
        runTest();
    }
}

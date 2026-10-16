package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.OptimizeOneRowPlan;

public class OptimizeOneRowPlanTest {

    public static void runTest() {
        var rule = new OptimizeOneRowPlan();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running OptimizeOneRowPlan test...");
        runTest();
    }
}

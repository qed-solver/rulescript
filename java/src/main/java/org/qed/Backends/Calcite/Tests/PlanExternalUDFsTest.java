package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PlanExternalUDFs;

public class PlanExternalUDFsTest {

    public static void runTest() {
        var rule = new PlanExternalUDFs();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PlanExternalUDFs test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.GroupBasedRowLevelOperationScanPlanning;

public class GroupBasedRowLevelOperationScanPlanningTest {

    public static void runTest() {
        var rule = new GroupBasedRowLevelOperationScanPlanning();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running GroupBasedRowLevelOperationScanPlanning test...");
        runTest();
    }
}

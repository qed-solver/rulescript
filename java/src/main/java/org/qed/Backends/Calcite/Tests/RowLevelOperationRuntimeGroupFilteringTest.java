package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.RowLevelOperationRuntimeGroupFiltering;

public class RowLevelOperationRuntimeGroupFilteringTest {

    public static void runTest() {
        var rule = new RowLevelOperationRuntimeGroupFiltering();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RowLevelOperationRuntimeGroupFiltering test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PartitionPruning;

public class PartitionPruningTest {

    public static void runTest() {
        var rule = new PartitionPruning();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PartitionPruning test...");
        runTest();
    }
}

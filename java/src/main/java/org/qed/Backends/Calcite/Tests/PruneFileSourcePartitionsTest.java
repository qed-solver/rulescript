package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PruneFileSourcePartitions;

public class PruneFileSourcePartitionsTest {

    public static void runTest() {
        var rule = new PruneFileSourcePartitions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneFileSourcePartitions test...");
        runTest();
    }
}

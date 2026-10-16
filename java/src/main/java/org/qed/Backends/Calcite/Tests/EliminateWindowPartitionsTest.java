package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateWindowPartitions;

public class EliminateWindowPartitionsTest {

    public static void runTest() {
        var rule = new EliminateWindowPartitions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateWindowPartitions test...");
        runTest();
    }
}

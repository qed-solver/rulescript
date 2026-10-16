package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.SparkEliminateDistinct;

public class SparkEliminateDistinctTest {

    public static void runTest() {
        var rule = new SparkEliminateDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running SparkEliminateDistinct test...");
        runTest();
    }
}

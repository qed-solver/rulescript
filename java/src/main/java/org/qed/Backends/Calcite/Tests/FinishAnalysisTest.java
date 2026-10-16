package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.FinishAnalysis;

public class FinishAnalysisTest {

    public static void runTest() {
        var rule = new FinishAnalysis();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running FinishAnalysis test...");
        runTest();
    }
}

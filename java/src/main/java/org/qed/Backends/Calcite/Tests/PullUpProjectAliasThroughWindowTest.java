package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PullUpProjectAliasThroughWindow;

public class PullUpProjectAliasThroughWindowTest {

    public static void runTest() {
        var rule = new PullUpProjectAliasThroughWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PullUpProjectAliasThroughWindow test...");
        runTest();
    }
}

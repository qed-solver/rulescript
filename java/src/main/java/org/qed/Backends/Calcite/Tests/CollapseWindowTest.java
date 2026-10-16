package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.CollapseWindow;

public class CollapseWindowTest {

    public static void runTest() {
        var rule = new CollapseWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running CollapseWindow test...");
        runTest();
    }
}

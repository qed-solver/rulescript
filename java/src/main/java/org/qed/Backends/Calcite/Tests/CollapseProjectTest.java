package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.CollapseProject;

public class CollapseProjectTest {

    public static void runTest() {
        var rule = new CollapseProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running CollapseProject test...");
        runTest();
    }
}

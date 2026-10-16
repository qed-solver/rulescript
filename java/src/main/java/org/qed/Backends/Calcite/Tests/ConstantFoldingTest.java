package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ConstantFolding;

public class ConstantFoldingTest {

    public static void runTest() {
        var rule = new ConstantFolding();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ConstantFolding test...");
        runTest();
    }
}

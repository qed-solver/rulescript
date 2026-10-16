package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ConstantPropagation;

public class ConstantPropagationTest {

    public static void runTest() {
        var rule = new ConstantPropagation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ConstantPropagation test...");
        runTest();
    }
}

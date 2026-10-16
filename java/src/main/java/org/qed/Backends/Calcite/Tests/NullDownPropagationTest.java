package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.NullDownPropagation;

public class NullDownPropagationTest {

    public static void runTest() {
        var rule = new NullDownPropagation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running NullDownPropagation test...");
        runTest();
    }
}

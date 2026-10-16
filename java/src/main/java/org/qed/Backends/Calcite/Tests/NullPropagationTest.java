package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.NullPropagation;

public class NullPropagationTest {

    public static void runTest() {
        var rule = new NullPropagation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running NullPropagation test...");
        runTest();
    }
}

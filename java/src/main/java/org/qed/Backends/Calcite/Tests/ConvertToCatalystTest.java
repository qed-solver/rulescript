package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ConvertToCatalyst;

public class ConvertToCatalystTest {

    public static void runTest() {
        var rule = new ConvertToCatalyst();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ConvertToCatalyst test...");
        runTest();
    }
}

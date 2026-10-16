package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ReplaceExceptWithFilter;

public class ReplaceExceptWithFilterTest {

    public static void runTest() {
        var rule = new ReplaceExceptWithFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceExceptWithFilter test...");
        runTest();
    }
}

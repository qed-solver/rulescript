package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ReplaceCTERefWithRepartition;

public class ReplaceCTERefWithRepartitionTest {

    public static void runTest() {
        var rule = new ReplaceCTERefWithRepartition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceCTERefWithRepartition test...");
        runTest();
    }
}

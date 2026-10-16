package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PullOutVariantExtractions;

public class PullOutVariantExtractionsTest {

    public static void runTest() {
        var rule = new PullOutVariantExtractions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PullOutVariantExtractions test...");
        runTest();
    }
}

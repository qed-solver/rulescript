package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushVariantIntoScan;

public class PushVariantIntoScanTest {

    public static void runTest() {
        var rule = new PushVariantIntoScan();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PushVariantIntoScan test...");
        runTest();
    }
}

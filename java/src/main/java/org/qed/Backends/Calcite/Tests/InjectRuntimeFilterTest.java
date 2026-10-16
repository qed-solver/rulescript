package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.InjectRuntimeFilter;

public class InjectRuntimeFilterTest {

    public static void runTest() {
        var rule = new InjectRuntimeFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running InjectRuntimeFilter test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.CollapseRepartition;

public class CollapseRepartitionTest {

    public static void runTest() {
        var rule = new CollapseRepartition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running CollapseRepartition test...");
        runTest();
    }
}

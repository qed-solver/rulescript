package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.CombineUnions;

public class CombineUnionsTest {

    public static void runTest() {
        var rule = new CombineUnions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running CombineUnions test...");
        runTest();
    }
}

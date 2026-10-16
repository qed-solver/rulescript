package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.OptimizeUpdateFields;

public class OptimizeUpdateFieldsTest {

    public static void runTest() {
        var rule = new OptimizeUpdateFields();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running OptimizeUpdateFields test...");
        runTest();
    }
}

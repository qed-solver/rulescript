package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ConvertToLocalRelation;

public class ConvertToLocalRelationTest {

    public static void runTest() {
        var rule = new ConvertToLocalRelation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ConvertToLocalRelation test...");
        runTest();
    }
}

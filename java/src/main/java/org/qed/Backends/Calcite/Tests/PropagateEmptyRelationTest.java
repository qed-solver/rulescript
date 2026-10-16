package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PropagateEmptyRelation;

public class PropagateEmptyRelationTest {

    public static void runTest() {
        var rule = new PropagateEmptyRelation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PropagateEmptyRelation test...");
        runTest();
    }
}

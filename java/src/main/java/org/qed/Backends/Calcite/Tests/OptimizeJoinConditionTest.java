package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.OptimizeJoinCondition;

public class OptimizeJoinConditionTest {

    public static void runTest() {
        var rule = new OptimizeJoinCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running OptimizeJoinCondition test...");
        runTest();
    }
}

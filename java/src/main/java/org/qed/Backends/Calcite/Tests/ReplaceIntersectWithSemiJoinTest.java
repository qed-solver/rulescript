package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ReplaceIntersectWithSemiJoin;

public class ReplaceIntersectWithSemiJoinTest {

    public static void runTest() {
        var rule = new ReplaceIntersectWithSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceIntersectWithSemiJoin test...");
        runTest();
    }
}

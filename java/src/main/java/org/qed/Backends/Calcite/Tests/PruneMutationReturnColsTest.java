package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PruneMutationReturnCols;

public class PruneMutationReturnColsTest {
    public static void runTest() {
        System.out.println("Running RULE test...");
        var rule = new PruneMutationReturnCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneMutationReturnCols rule) {
        RuleChecks.rewrites(rule, FaithfulPlans.of(rule.before()), FaithfulPlans.expected(rule.after()));
    }
}

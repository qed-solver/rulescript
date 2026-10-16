package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushFoldableIntoBranches;

public class PushFoldableIntoBranchesTest {

    public static void runTest() {
        var rule = new PushFoldableIntoBranches();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PushFoldableIntoBranches test...");
        runTest();
    }
}

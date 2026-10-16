package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PushdownPredicatesAndPruneColumnsForCTEDef;

public class PushdownPredicatesAndPruneColumnsForCTEDefTest {

    public static void runTest() {
        var rule = new PushdownPredicatesAndPruneColumnsForCTEDef();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running PushdownPredicatesAndPruneColumnsForCTEDef test...");
        runTest();
    }
}

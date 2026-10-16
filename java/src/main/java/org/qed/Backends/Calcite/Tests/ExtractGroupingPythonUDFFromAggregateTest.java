package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ExtractGroupingPythonUDFFromAggregate;

public class ExtractGroupingPythonUDFFromAggregateTest {

    public static void runTest() {
        var rule = new ExtractGroupingPythonUDFFromAggregate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ExtractGroupingPythonUDFFromAggregate test...");
        runTest();
    }
}

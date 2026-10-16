package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ExtractPythonUDFFromJoinCondition;

public class ExtractPythonUDFFromJoinConditionTest {

    public static void runTest() {
        var rule = new ExtractPythonUDFFromJoinCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ExtractPythonUDFFromJoinCondition test...");
        runTest();
    }
}

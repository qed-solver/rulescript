package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ExtractPythonUDFs;

public class ExtractPythonUDFsTest {

    public static void runTest() {
        var rule = new ExtractPythonUDFs();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running ExtractPythonUDFs test...");
        runTest();
    }
}

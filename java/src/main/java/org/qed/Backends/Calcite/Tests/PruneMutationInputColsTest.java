package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PruneMutationInputCols;
import org.qed.RuleBuilder;

public class PruneMutationInputColsTest {
    public static void runTest() {
        var rule = new PruneMutationInputCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneMutationInputCols rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        builder.scan(table);
        var value = builder.field(1);
        var mutation = builder.genericProjectionOp("M", new org.qed.RelType.VarType("M_Type", true));
        var before = builder.project(Seq.of(builder.call(mutation, Seq.of(value)))).build();
        RuleChecks.doesNotApply(rule, before);
        System.out.println("succeeded (mutation depending on the second input column is preserved)");
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("Calcite rule test failures: " + CalciteTester.failures());
        }
    }
}

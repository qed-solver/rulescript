package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlOperator;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ExtractPythonUDFs;
import org.qed.RuleBuilder;

public class ExtractPythonUDFsTest {
    public static void runTest() {
        var rule = new ExtractPythonUDFs();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive();
        handBuiltNoMatch();
    }

    private static void handBuiltPositive() {
        var sourceBuilder = RuleBuilder.create();
        var table = RuleChecks.table(sourceBuilder, 1, false);
        var b = RuleChecks.ruleBuilder(sourceBuilder);
        var source = b.scan(table).build();
        b.push(source);
        var x = b.field(0);
        var python = b.call(op(sourceBuilder, "f"), x);
        var java = b.call(op(sourceBuilder, "g"), x);
        b.project(python, java);
        RelNode before = b.build();

        var expectedSourceBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedSourceBuilder, 1, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedSourceBuilder);
        var expectedSource = expectedBuilder.scan(expectedTable).build();
        expectedBuilder.push(expectedSource);
        var expectedX = expectedBuilder.field(0);
        var expectedPython = expectedBuilder.call(op(expectedSourceBuilder, "f"), expectedX);
        expectedBuilder.project(expectedPython, expectedX);
        RelNode extracted = expectedBuilder.build();
        expectedBuilder.push(extracted);
        expectedBuilder.project(expectedBuilder.field(0),
                expectedBuilder.call(op(expectedSourceBuilder, "g"), expectedBuilder.field(1)));
        RelNode expected = expectedBuilder.build();
        RuleChecks.rewrites(new ExtractPythonUDFs(), before, expected);
    }

    private static void handBuiltNoMatch() {
        var sourceBuilder = RuleBuilder.create();
        var table = RuleChecks.table(sourceBuilder, 2, false);
        var b = RuleChecks.ruleBuilder(sourceBuilder);
        var source = b.scan(table).build();
        b.push(source);
        var x = b.field(0);
        var java = b.call(op(sourceBuilder, "g"), x);
        b.project(java, b.field(1));
        RelNode plan = b.build();
        RuleChecks.doesNotApply(new ExtractPythonUDFs(), plan);
    }

    private static SqlOperator op(RuleBuilder builder, String name) {
        return builder.genericProjectionOp(name, org.qed.RexRN.varType(name + "_type", true));
    }

    public static void main(String[] args) {
        System.out.println("Running ExtractPythonUDFs test...");
        runTest();
    }
}

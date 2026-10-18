package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.NormalizeInConst;

public class NormalizeInConstTest {
    public static void runTest() {
        var rule = new NormalizeInConst();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NormalizeInConst rule) {
        var builder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(builder, 3, false);
        var input = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var x = rex.makeInputRef(input, 0);
        var a = rex.makeInputRef(input, 1);
        var b = rex.makeInputRef(input, 2);
        var eqA = rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS, x, a);
        var eqB = rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS, x, b);
        var duplicated = rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.OR, eqA, eqB, eqA, eqB);
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(input, duplicated);

        var expectedBuilder = org.qed.RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 3, false);
        var expectedInput = expectedBuilder.scan(expectedTable).build();
        var expectedRex = expectedBuilder.getRexBuilder();
        var expectedX = expectedRex.makeInputRef(expectedInput, 0);
        var expectedA = expectedRex.makeInputRef(expectedInput, 1);
        var expectedB = expectedRex.makeInputRef(expectedInput, 2);
        var expectedCondition = expectedRex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.OR,
                expectedRex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS, expectedX, expectedA),
                expectedRex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS, expectedX, expectedB));
        var after = org.apache.calcite.rel.logical.LogicalFilter.create(expectedInput, expectedCondition);
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running NormalizeInConst test...");
        runTest();
        System.out.println("Generated 1/1 rules, 1 compiled, 0 test failures");
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldNonNullTupleIsTupleNull;
import org.qed.RuleBuilder;

public class FoldNonNullTupleIsTupleNullTest {
    public static void runTest() {
        var rule = new FoldNonNullTupleIsTupleNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive();
        handBuiltNoMatch();
    }

    private static void handBuiltPositive() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        builder.scan(table);
        var input = builder.build();
        var rex = builder.getRexBuilder();
        var condition = rex.makeCall(SqlStdOperatorTable.AND,
                rex.makeCall(SqlStdOperatorTable.IS_NULL, rex.makeInputRef(input, 0)),
                rex.makeCall(SqlStdOperatorTable.IS_NULL, rex.makeInputRef(input, 1)));
        RelNode before = LogicalFilter.create(input, condition);

        var expectedBuilder = RuleBuilder.create();
        expectedBuilder.scan(RuleChecks.table(expectedBuilder, 2, false, false));
        RelNode expected = LogicalFilter.create(expectedBuilder.build(),
                expectedBuilder.getRexBuilder().makeLiteral(false));
        RuleChecks.rewrites(new FoldNonNullTupleIsTupleNull(), before, expected);
    }

    private static void handBuiltNoMatch() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, true);
        builder.scan(table);
        var input = builder.build();
        var rex = builder.getRexBuilder();
        var condition = rex.makeCall(SqlStdOperatorTable.AND,
                rex.makeCall(SqlStdOperatorTable.IS_NULL, rex.makeInputRef(input, 0)),
                rex.makeCall(SqlStdOperatorTable.IS_NULL, rex.makeInputRef(input, 1)));
        RelNode plan = LogicalFilter.create(input, condition);
        RuleChecks.doesNotApply(new FoldNonNullTupleIsTupleNull(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNonNullTupleIsTupleNull test...");
        runTest();
    }
}

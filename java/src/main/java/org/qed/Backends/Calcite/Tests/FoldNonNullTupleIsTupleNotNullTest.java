package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldNonNullTupleIsTupleNotNull;
import org.qed.QedTable;
import org.qed.RelType;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public class FoldNonNullTupleIsTupleNotNullTest {
    public static void runTest() {
        var rule = new FoldNonNullTupleIsTupleNotNull();
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
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.AND,
                builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL, builder.getRexBuilder().makeInputRef(input, 0)),
                builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL, builder.getRexBuilder().makeInputRef(input, 1)));
        var before = LogicalFilter.create(input, condition);

        var expectedBuilder = RuleBuilder.create();
        expectedBuilder.scan(RuleChecks.table(expectedBuilder, 2, false, false));
        var expected = expectedBuilder.build();
        RuleChecks.rewrites(new FoldNonNullTupleIsTupleNotNull(), before, expected);
    }

    private static void handBuiltNoMatch() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, true);
        builder.scan(table);
        var input = builder.build();
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.AND,
                builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL, builder.getRexBuilder().makeInputRef(input, 0)),
                builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL, builder.getRexBuilder().makeInputRef(input, 1)));
        var plan = LogicalFilter.create(input, condition);
        RuleChecks.doesNotApply(new FoldNonNullTupleIsTupleNotNull(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNonNullTupleIsTupleNotNull test...");
        runTest();
    }
}

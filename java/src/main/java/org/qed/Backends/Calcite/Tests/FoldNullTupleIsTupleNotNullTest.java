package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldNullTupleIsTupleNotNull;
import org.qed.RuleBuilder;
import org.qed.RelRN;
import org.qed.RexRN;

public final class FoldNullTupleIsTupleNotNullTest {
    public static void runTest() {
        var rule = new FoldNullTupleIsTupleNotNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesNullElementWithNullableSibling(rule);
        rewritesNullElementWithNonNullableSibling(rule);
        rejectsTupleNotNullWithoutNullElement(rule);
    }

    private static void rewritesNullElementWithNullableSibling(FoldNullTupleIsTupleNotNull rule) {
        var qed = RuleBuilder.create();
        var input = RuleChecks.table(qed, 1, false, true);
        var source = qed.transform(CalciteRuntime::configure);
        var scan = source.scan(input).build();
        var rex = source.getRexBuilder();
        var nullValue = rex.makeNullLiteral(source.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var nullableValue = rex.makeInputRef(scan, 0);
        var condition = rex.makeCall(SqlStdOperatorTable.AND,
                rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, nullValue),
                rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, nullableValue));
        var before = LogicalFilter.create(scan, condition);
        var expected = LogicalFilter.create(scan, rex.makeLiteral(false));
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rewritesNullElementWithNonNullableSibling(FoldNullTupleIsTupleNotNull rule) {
        var qed = RuleBuilder.create();
        var input = RuleChecks.table(qed, 1, false, false);
        var source = qed.transform(CalciteRuntime::configure);
        var scan = source.scan(input).build();
        var rex = source.getRexBuilder();
        var nullValue = rex.makeNullLiteral(source.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var nonNullableValue = rex.makeInputRef(scan, 0);
        var condition = rex.makeCall(SqlStdOperatorTable.AND,
                rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, nullValue),
                rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, nonNullableValue));
        var before = LogicalFilter.create(scan, condition);
        var expected = LogicalFilter.create(scan, rex.makeLiteral(false));
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsTupleNotNullWithoutNullElement(FoldNullTupleIsTupleNotNull rule) {
        var source = RelRN.scan("Other", "Other_Type");
        var condition = source.field(0).pred(SqlStdOperatorTable.IS_NOT_NULL);
        var before = FaithfulPlans.of(source.filter(condition));
        RuleChecks.doesNotApply(rule, before);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullTupleIsTupleNotNull test...");
        runTest();
    }
}

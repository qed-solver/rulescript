package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.FoldNullTupleIsTupleNull;
import org.qed.RuleBuilder;
import org.qed.RelRN;

public final class FoldNullTupleIsTupleNullTest {
    public static void runTest() {
        var rule = new FoldNullTupleIsTupleNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesConcreteNullTuple(rule);
        rejectsTupleWithNonNullElement(rule);
    }

    private static void rewritesConcreteNullTuple(FoldNullTupleIsTupleNull rule) {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false);
        var source = qed.transform(CalciteRuntime::configure);
        var scan = source.scan(table).build();
        var rex = source.getRexBuilder();
        var nullValue = rex.makeNullLiteral(source.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var first = rex.makeCall(SqlStdOperatorTable.IS_NULL, nullValue);
        var second = rex.makeCall(SqlStdOperatorTable.IS_NULL, nullValue);
        var condition = rex.makeCall(SqlStdOperatorTable.AND, Seq.of(first, second).asJava());
        var before = LogicalFilter.create(scan, condition);
        var expected = source.scan(table).build();
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsTupleWithNonNullElement(FoldNullTupleIsTupleNull rule) {
        var source = RelRN.scan("Other", "Other_Type");
        var before = FaithfulPlans.of(source.filter(source.field(0).pred(SqlStdOperatorTable.IS_NULL)));
        RuleChecks.doesNotApply(rule, before);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullTupleIsTupleNull test...");
        runTest();
    }
}

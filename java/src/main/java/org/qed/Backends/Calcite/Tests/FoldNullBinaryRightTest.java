package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.FoldNullBinaryRight;
import org.qed.RelType;
import org.qed.RuleBuilder;

public final class FoldNullBinaryRightTest {
    public static void runTest() {
        var rule = new FoldNullBinaryRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        matchesTypedNullComparison();
        rejectsOtherOperator();
        rejectsNonNullRightOperand();
        rejectsMultipleColumns();
    }

    private static RuleBuilder sourceBuilder() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table).scan(table.getName());
        return builder;
    }

    private static void matchesTypedNullComparison() {
        var builder = sourceBuilder();
        var input = builder.peek();
        var rex = builder.getRexBuilder();
        RexNode condition = rex.makeCall(SqlStdOperatorTable.LESS_THAN,
                rex.makeInputRef(input, 0), rex.makeNullLiteral(input.getRowType().getFieldList().get(0).getType()));
        var before = LogicalFilter.create(input, condition);
        var expected = LogicalFilter.create(input, rex.makeNullLiteral(builder.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN)));
        RuleChecks.rewrites(new FoldNullBinaryRight(), before, expected);
    }

    private static void rejectsOtherOperator() {
        var builder = sourceBuilder();
        var input = builder.peek();
        var rex = builder.getRexBuilder();
        var nullValue = rex.makeNullLiteral(input.getRowType().getFieldList().get(0).getType());
        RelNode nearMiss = LogicalFilter.create(input,
                rex.makeCall(SqlStdOperatorTable.GREATER_THAN, rex.makeInputRef(input, 0), nullValue));
        RuleChecks.doesNotApply(new FoldNullBinaryRight(), nearMiss);
    }

    private static void rejectsNonNullRightOperand() {
        var builder = sourceBuilder();
        var input = builder.peek();
        var rex = builder.getRexBuilder();
        RelNode nearMiss = LogicalFilter.create(input, rex.makeCall(SqlStdOperatorTable.LESS_THAN,
                rex.makeInputRef(input, 0), rex.makeExactLiteral(java.math.BigDecimal.valueOf(4),
                        input.getRowType().getFieldList().get(0).getType())));
        RuleChecks.doesNotApply(new FoldNullBinaryRight(), nearMiss);
    }

    private static void rejectsMultipleColumns() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false),
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table).scan(table.getName());
        var input = builder.peek();
        var rex = builder.getRexBuilder();
        RelNode nearMiss = LogicalFilter.create(input, rex.makeCall(SqlStdOperatorTable.LESS_THAN,
                rex.makeInputRef(input, 0), rex.makeNullLiteral(input.getRowType().getFieldList().get(0).getType())));
        RuleChecks.doesNotApply(new FoldNullBinaryRight(), nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullBinaryRight test...");
        runTest();
    }
}

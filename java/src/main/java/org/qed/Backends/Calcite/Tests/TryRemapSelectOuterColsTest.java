package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.TryRemapSelectOuterCols;
import org.qed.RuleBuilder;

public class TryRemapSelectOuterColsTest {
    public static void runTest() {
        var rule = new TryRemapSelectOuterCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltRewrite(rule);
        rejectsWrongInputArity(rule);
    }

    private static void handBuiltRewrite(TryRemapSelectOuterCols rule) {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false),
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false),
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var scan = builder.scan(table.getName());
        var h = RuleBuilder.create().genericPredicateOp("h", true);
        var before = scan.filter(builder.call(h, builder.field(0)))
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1))).build();
        var expectedBuilder = RuleBuilder.create();
        expectedBuilder.addTable(new org.qed.QedTable(table.getName(), table.getColumnNames(), table.getColumnTypes(),
                table.getKeys(), table.getConstraints()));
        var expected = expectedBuilder.scan(table.getName())
                .filter(expectedBuilder.call(h, expectedBuilder.field(1)))
                .filter(expectedBuilder.call(SqlStdOperatorTable.EQUALS, expectedBuilder.field(0), expectedBuilder.field(1))).build();
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsWrongInputArity(TryRemapSelectOuterCols rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, true);
        var h = RuleBuilder.create().genericPredicateOp("h", true);
        var plan = builder.scan(table)
                .filter(builder.call(h, builder.field(0)))
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1))).build();
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running TryRemapSelectOuterCols test...");
        runTest();
    }
}

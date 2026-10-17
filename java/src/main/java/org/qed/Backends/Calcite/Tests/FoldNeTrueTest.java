package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.FoldNeTrue;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class FoldNeTrueTest {
    public static void runTest() {
        var rule = new FoldNeTrue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsDifferentLiteral();
    }

    private static void rewritesHandBuiltPlan(FoldNeTrue rule) {
        var builder = booleanTable();
        var scan = builder.scan("Table_0").build();
        var ref = builder.getRexBuilder().makeInputRef(scan, 0);
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT_EQUALS,
                ref, builder.getRexBuilder().makeLiteral(true));
        var before = LogicalFilter.create(scan, condition);
        var expected = LogicalFilter.create(scan,
                builder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT, ref));
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsDifferentLiteral() {
        var builder = booleanTable();
        var scan = builder.scan("Table_0").build();
        var ref = builder.getRexBuilder().makeInputRef(scan, 0);
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT_EQUALS,
                ref, builder.getRexBuilder().makeLiteral(false));
        RuleChecks.doesNotApply(new FoldNeTrue(), LogicalFilter.create(scan, condition));
    }

    private static RelBuilder booleanTable() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(Tuple.of(RelType.fromString("BOOLEAN", true), false)));
        builder.addTable(table);
        return builder.transform(CalciteRuntime::configure);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNeTrue test...");
        runTest();
    }
}

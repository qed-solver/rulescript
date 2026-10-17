package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNeFalse;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class FoldNeFalseTest {
    public static void runTest() {
        var rule = new FoldNeFalse();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsDifferentLiteral();
    }

    private static void rewritesHandBuiltPlan(FoldNeFalse rule) {
        var builder = booleanTable();
        var scan = builder.scan("Table_0").build();
        var ref = builder.getRexBuilder().makeInputRef(scan, 0);
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT_EQUALS,
                ref, builder.getRexBuilder().makeLiteral(false));
        var before = LogicalFilter.create(scan, condition);
        var expected = LogicalFilter.create(scan, ref);
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsDifferentLiteral() {
        var builder = booleanTable();
        var scan = builder.scan("Table_0").build();
        RexNode ref = builder.getRexBuilder().makeInputRef(scan, 0);
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT_EQUALS,
                ref, builder.getRexBuilder().makeLiteral(true));
        RuleChecks.doesNotApply(new FoldNeFalse(), LogicalFilter.create(scan, condition));
    }

    private static RuleBuilder booleanTable() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(Tuple.of(RelType.fromString("BOOLEAN", true), false)));
        builder.addTable(table);
        return builder;
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNeFalse test...");
        runTest();
    }
}

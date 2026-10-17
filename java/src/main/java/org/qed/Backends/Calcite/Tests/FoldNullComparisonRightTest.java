package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNullComparisonRight;
import org.qed.RuleBuilder;
import org.qed.RelType;

public class FoldNullComparisonRightTest {
    public static void runTest() {
        var rule = new FoldNullComparisonRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltFilter();
        rejectsReversedOperands();
    }

    private static void rewritesHandBuiltFilter() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(kala.tuple.Tuple.of(RelType.fromString("BOOLEAN", true), false)));
        builder.addTable(table);
        var input = RuleChecks.ruleBuilder(builder).scan(table.getName()).build();
        var rexBuilder = RuleChecks.ruleBuilder(builder).getRexBuilder();
        var column = rexBuilder.makeInputRef(input, 0);
        var nullBoolean = rexBuilder.makeNullLiteral(rexBuilder.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var before = LogicalFilter.create(input,
                rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, column, nullBoolean));
        RelNode expected = LogicalFilter.create(input, nullBoolean);
        RuleChecks.rewrites(new FoldNullComparisonRight(), before, expected);
    }

    private static void rejectsReversedOperands() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(kala.tuple.Tuple.of(RelType.fromString("BOOLEAN", true), false)));
        builder.addTable(table);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        var input = relBuilder.scan(table.getName()).build();
        var rexBuilder = relBuilder.getRexBuilder();
        RexNode column = rexBuilder.makeInputRef(input, 0);
        var nullBoolean = rexBuilder.makeNullLiteral(rexBuilder.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var reversed = LogicalFilter.create(input,
                rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, nullBoolean, column));
        RuleChecks.doesNotApply(new FoldNullComparisonRight(), reversed);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullComparisonRight test...");
        runTest();
    }
}

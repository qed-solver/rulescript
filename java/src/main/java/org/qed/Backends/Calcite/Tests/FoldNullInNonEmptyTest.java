package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRule;
import org.qed.RRuleInstances.FoldNullInNonEmpty;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public final class FoldNullInNonEmptyTest {
    public static void runTest() {
        var rule = new FoldNullInNonEmpty();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch();
        handBuiltNoMatch();
    }

    private static void handBuiltMatch() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var input = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var nullInt = rex.makeNullLiteral(builder.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var first = rex.makeInputRef(input.getRowType().getFieldList().get(0).getType(), 0);
        var second = rex.makeInputRef(input.getRowType().getFieldList().get(1).getType(), 1);
        var equalsFirst = rex.makeCall(SqlStdOperatorTable.EQUALS, nullInt, first);
        var equalsSecond = rex.makeCall(SqlStdOperatorTable.EQUALS, nullInt, second);
        var source = org.apache.calcite.rel.logical.LogicalFilter.create(input,
                rex.makeCall(SqlStdOperatorTable.OR, equalsFirst, equalsSecond));

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 2, false);
        var expectedInput = expectedBuilder.scan(expectedTable).build();
        var nullBoolean = expectedBuilder.getRexBuilder().makeNullLiteral(
                expectedBuilder.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var expected = org.apache.calcite.rel.logical.LogicalFilter.create(expectedInput, nullBoolean);
        RuleChecks.rewrites(new FoldNullInNonEmpty(), source, expected);
    }

    private static void handBuiltNoMatch() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var input = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var first = rex.makeInputRef(input.getRowType().getFieldList().get(0).getType(), 0);
        var second = rex.makeInputRef(input.getRowType().getFieldList().get(1).getType(), 1);
        var condition = rex.makeCall(SqlStdOperatorTable.OR,
                rex.makeCall(SqlStdOperatorTable.EQUALS, first, second),
                rex.makeCall(SqlStdOperatorTable.EQUALS, second, first));
        RuleChecks.doesNotApply(new FoldNullInNonEmpty(),
                org.apache.calcite.rel.logical.LogicalFilter.create(input, condition));
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullInNonEmpty test...");
        runTest();
    }
}

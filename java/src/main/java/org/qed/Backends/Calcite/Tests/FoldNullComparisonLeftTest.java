package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNullComparisonLeft;
import org.qed.RuleBuilder;

public final class FoldNullComparisonLeftTest {
    public static void runTest() {
        var rule = new FoldNullComparisonLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsReversedComparison();
    }

    private static void rewritesHandBuiltPlan() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        var source = builder.scan(table).build();
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                builder.getRexBuilder().makeNullLiteral(source.getRowType().getFieldList().get(0).getType()),
                builder.getRexBuilder().makeInputRef(source, 0));
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(source, condition);

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false, true);
        var expectedSource = expectedBuilder.scan(expectedTable).build();
        var expected = org.apache.calcite.rel.logical.LogicalFilter.create(expectedSource,
                expectedBuilder.getRexBuilder().makeNullLiteral(expectedBuilder.getTypeFactory()
                        .createSqlType(org.apache.calcite.sql.type.SqlTypeName.BOOLEAN)));
        RuleChecks.rewrites(new FoldNullComparisonLeft(), before, expected);
    }

    private static void rejectsReversedComparison() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        var source = builder.scan(table).build();
        var reversedCondition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                builder.getRexBuilder().makeInputRef(source, 0),
                builder.getRexBuilder().makeNullLiteral(source.getRowType().getFieldList().get(0).getType()));
        RelNode reversed = org.apache.calcite.rel.logical.LogicalFilter.create(source, reversedCondition);
        RuleChecks.doesNotApply(new FoldNullComparisonLeft(), reversed);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullComparisonLeft test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNullBinaryLeft;
import org.qed.RuleBuilder;

public class FoldNullBinaryLeftTest {
    public static void runTest() {
        var rule = new FoldNullBinaryLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltNullLeftComparison();
        rejectsHandBuiltNonNullLeftComparison();
    }

    private static void rewritesHandBuiltNullLeftComparison() {
        var rule = new FoldNullBinaryLeft();
        var sourceBuilder = RuleBuilder.create();
        var sourceTable = RuleChecks.table(sourceBuilder, 1, false);
        var source = RuleChecks.ruleBuilder(sourceBuilder);
        var sourceRel = source.scan(sourceTable).build();
        var sourceRex = source.getRexBuilder();
        var comparison = sourceRex.makeCall(SqlStdOperatorTable.LESS_THAN,
                sourceRex.makeNullLiteral(sourceRel.getRowType().getFieldList().get(0).getType()),
                sourceRex.makeInputRef(sourceRel.getRowType().getFieldList().get(0).getType(), 0));
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(sourceRel, comparison);

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false);
        var expected = RuleChecks.ruleBuilder(expectedBuilder);
        var expectedRel = expected.scan(expectedTable).build();
        var after = org.apache.calcite.rel.logical.LogicalFilter.create(expectedRel,
                expected.getRexBuilder().makeNullLiteral(
                        expected.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.BOOLEAN)));
        RuleChecks.rewrites(rule, before, after);
    }

    private static void rejectsHandBuiltNonNullLeftComparison() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var rel = RuleChecks.ruleBuilder(builder);
        var plan = rel.scan(table)
                .filter(rel.call(SqlStdOperatorTable.LESS_THAN, rel.literal(7), rel.field(0)))
                .build();
        RuleChecks.doesNotApply(new FoldNullBinaryLeft(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullBinaryLeft test...");
        runTest();
    }
}

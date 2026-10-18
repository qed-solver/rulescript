package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldIsNotNull;
import org.qed.RuleBuilder;

public final class FoldIsNotNullTest {
    public static void runTest() {
        var rule = new FoldIsNotNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch(rule);
        nonNullLiteralControl();
    }

    private static void handBuiltMatch(FoldIsNotNull rule) {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, true);
        var builder = RuleChecks.ruleBuilder(qed);
        builder.scan(table);
        var nullInt = builder.getRexBuilder().makeNullLiteral(builder.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        RelNode before = LogicalFilter.create(builder.build(),
                builder.call(SqlStdOperatorTable.IS_NOT_NULL, nullInt));

        var expectedQed = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedQed, 1, false, true);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedQed);
        expectedBuilder.scan(expectedTable);
        RelNode expected = LogicalFilter.create(expectedBuilder.build(),
                expectedBuilder.getRexBuilder().makeLiteral(false));
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void nonNullLiteralControl() {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, true);
        var builder = RuleChecks.ruleBuilder(qed);
        builder.scan(table);
        RelNode plan = LogicalFilter.create(builder.build(),
                builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.literal(7)));
        RuleChecks.doesNotApply(new FoldIsNotNull(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldIsNotNull test...");
        runTest();
    }
}

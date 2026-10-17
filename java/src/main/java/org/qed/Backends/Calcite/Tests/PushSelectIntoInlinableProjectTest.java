package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushSelectIntoInlinableProject;

public final class PushSelectIntoInlinableProjectTest {
    private PushSelectIntoInlinableProjectTest() {}

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoInlinableProject test...");
        runTest();
    }

    public static void runTest() {
        var rule = new PushSelectIntoInlinableProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoInlinableProject rule) {
        var builder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, true);
        builder.scan(table);
        var a = builder.field(1, 0, 0);
        var b = builder.field(1, 0, 1);
        var f = builder.call(SqlStdOperatorTable.PLUS, a, builder.literal(1));
        var g = builder.call(SqlStdOperatorTable.PLUS, b, builder.literal(2));
        var plan = builder
                .project(f, g, builder.field(1, 0, 0), builder.field(1, 0, 1))
                .filter(builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(1, 0, 0), builder.literal(10)),
                        builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(1, 0, 1), builder.literal(20))))
                .build();

        var plain = RuleChecks.ruleBuilder(builder);
        plain.scan(table);
        var expectedF = plain.call(SqlStdOperatorTable.PLUS, plain.field(1, 0, 0), plain.literal(1));
        var expectedG = plain.call(SqlStdOperatorTable.PLUS, plain.field(1, 0, 1), plain.literal(2));
        var expected = plain
                .filter(plain.and(
                        plain.call(SqlStdOperatorTable.GREATER_THAN, expectedF, plain.literal(10)),
                        plain.call(SqlStdOperatorTable.LESS_THAN, expectedG, plain.literal(20))))
                .project(expectedF, expectedG, plain.field(1, 0, 0), plain.field(1, 0, 1))
                .build();
        RuleChecks.rewrites(rule, plan, expected);

        builder.scan(table);
        var nearA = builder.field(1, 0, 0);
        var nearB = builder.field(1, 0, 1);
        var nearF = builder.call(SqlStdOperatorTable.PLUS, nearA, builder.literal(1));
        var nearG = builder.call(SqlStdOperatorTable.PLUS, nearB, builder.literal(2));
        var nearMiss = builder
                .project(nearF, nearG, builder.field(1, 0, 0), builder.field(1, 0, 1))
                .filter(builder.and(
                        builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(1, 0, 1), builder.literal(20)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(1, 0, 0), builder.literal(10))))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }
}

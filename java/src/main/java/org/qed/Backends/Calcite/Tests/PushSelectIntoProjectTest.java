package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushSelectIntoProject;
import org.qed.RuleBuilder;

public class PushSelectIntoProjectTest {

    public static void runTest() {
        var rule = new PushSelectIntoProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoProject rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var before = builder.scan(table)
                .project(
                        builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.literal(1)),
                        builder.field(0))
                .filter(builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(1), builder.literal(5)),
                        builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(0), builder.literal(10))))
                .build();
        var after = b.scan(table)
                .filter(RuleChecks.gt(b, b.field(0), 5))
                .project(
                        b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)),
                        b.field(0))
                .filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(10)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var single = builder.scan(table)
                .project(
                        builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.literal(1)),
                        builder.field(0))
                .filter(RuleChecks.gt(builder, builder.field(1), 5))
                .build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoProject test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldGroupByAndWindow;
import org.qed.RuleBuilder;

public class FoldGroupByAndWindowTest {

    public static void runTest() {
        var rule = new FoldGroupByAndWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldGroupByAndWindow rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var input = builder.scan(table).build();
        var window = builder.push(input)
                .aggregate(builder.groupKey(0), builder.aggregateCall(SqlStdOperatorTable.SUM, builder.field(1)))
                .build();
        var before = builder.push(input).push(window)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .aggregate(builder.groupKey(0), builder.aggregateCall(SqlStdOperatorTable.SUM, builder.field(1)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .aggregate(plain.groupKey(0), plain.aggregateCall(SqlStdOperatorTable.SUM, plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var misJoined = builder.push(input).push(window)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 1), builder.field(2, 1, 0)))
                .aggregate(builder.groupKey(0), builder.aggregateCall(SqlStdOperatorTable.SUM, builder.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, misJoined);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldGroupByAndWindow test...");
        runTest();
    }
}

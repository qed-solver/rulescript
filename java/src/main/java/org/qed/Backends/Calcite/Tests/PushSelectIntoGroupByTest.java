package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushSelectIntoGroupBy;
import org.qed.RuleBuilder;

public class PushSelectIntoGroupByTest {

    public static void runTest() {
        var rule = new PushSelectIntoGroupBy();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoGroupBy rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var five = builder.literal(5);
        var hundred = builder.literal(100);
        var plain = RuleChecks.ruleBuilder(builder);
        var before = plain.scan(table)
                .aggregate(plain.groupKey(0), plain.aggregateCall(SqlStdOperatorTable.SUM, plain.field(1)))
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five),
                        plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(0), hundred))
                .build();
        var after = plain.scan(table)
                .filter(plain.and(
                        plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five),
                        plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(0), hundred)))
                .aggregate(plain.groupKey(0), plain.aggregateCall(SqlStdOperatorTable.SUM, plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var sumFiltered = builder.scan(table)
                .aggregate(builder.groupKey(0), builder.aggregateCall(SqlStdOperatorTable.SUM, builder.field(1)))
                .filter(builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(1), five)))
                .build();
        RuleChecks.doesNotApply(rule, sumFiltered);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoGroupBy test...");
        runTest();
    }
}

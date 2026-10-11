package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushSelectIntoUnorderedDistinctOn;
import org.qed.RuleBuilder;

public class PushSelectIntoUnorderedDistinctOnTest {

    public static void runTest() {
        var rule = new PushSelectIntoUnorderedDistinctOn();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoUnorderedDistinctOn rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var five = builder.literal(5);
        var ten = builder.literal(10);
        var plain = RuleChecks.ruleBuilder(builder);
        var before = plain.scan(table)
                .aggregate(plain.groupKey(0, 1))
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five),
                        plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(1), ten))
                .build();
        var after = plain.scan(table)
                .filter(plain.and(
                        plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five),
                        plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(1), ten)))
                .aggregate(plain.groupKey(0, 1))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var singleKey = builder.scan(table)
                .aggregate(builder.groupKey(0))
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five))
                .build();
        RuleChecks.doesNotApply(rule, singleKey);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoUnorderedDistinctOn test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.HoistSelectAboveUnorderedDistinctOn;
import org.qed.RuleBuilder;

public class HoistSelectAboveUnorderedDistinctOnTest {

    public static void runTest() {
        var rule = new HoistSelectAboveUnorderedDistinctOn();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(HoistSelectAboveUnorderedDistinctOn rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var five = builder.literal(5);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five))
                .aggregate(builder.groupKey(0, 1))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .aggregate(plain.groupKey(0, 1))
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var single = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five))
                .aggregate(builder.groupKey(0))
                .build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running HoistSelectAboveUnorderedDistinctOn test...");
        runTest();
    }
}

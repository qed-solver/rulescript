package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.DeduplicateSelectFilters;
import org.qed.RuleBuilder;

public class DeduplicateSelectFiltersTest {

    public static void runTest() {
        var rule = new DeduplicateSelectFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(DeduplicateSelectFilters rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var ten = builder.literal(10);
        var seven = builder.literal(7);
        var p = builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five);
        var q = builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(0), ten);
        var before = builder.scan(table)
                .filter(p)
                .filter(q)
                .filter(p)
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var pPlain = plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five);
        var qPlain = plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(0), ten);
        var after = plain.scan(table)
                .filter(pPlain)
                .filter(qPlain)
                .build();
        RuleChecks.rewrites(rule, before, after);

        var twoFilters = builder.scan(table)
                .filter(p)
                .filter(q)
                .build();
        RuleChecks.doesNotApply(rule, twoFilters);

        var differentThird = builder.scan(table)
                .filter(p)
                .filter(q)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), seven))
                .build();
        RuleChecks.doesNotApply(rule, differentThird);
    }

    public static void main(String[] args) {
        System.out.println("Running DeduplicateSelectFilters test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldEqFalse;
import org.qed.RuleBuilder;

public class FoldEqFalseTest {

    public static void runTest() {
        var rule = new FoldEqFalse();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldEqFalse rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.EQUALS,
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five),
                        builder.literal(false)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.not(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.EQUALS,
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five),
                        builder.literal(true)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldEqFalse test...");
        runTest();
    }
}

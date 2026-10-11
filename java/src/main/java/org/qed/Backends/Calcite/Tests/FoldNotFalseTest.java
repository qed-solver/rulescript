package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNotFalse;
import org.qed.RuleBuilder;

public class FoldNotFalseTest {

    public static void runTest() {
        var rule = new FoldNotFalse();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldNotFalse rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT, builder.literal(false)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.literal(true))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT,
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), builder.literal(5))))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNotFalse test...");
        runTest();
    }
}

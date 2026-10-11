package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNonNullIsNull;
import org.qed.RuleBuilder;

public class FoldNonNullIsNullTest {

    public static void runTest() {
        var rule = new FoldNonNullIsNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nonNullColumns(rule);
    }

    private static void nonNullColumns(FoldNonNullIsNull rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.IS_NULL, builder.field(0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table).filter(plain.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        var nullableBuilder = RuleBuilder.create();
        var nullable = RuleChecks.table(nullableBuilder, 1, false, true);
        var nearMiss = nullableBuilder.scan(nullable)
                .filter(nullableBuilder.call(SqlStdOperatorTable.IS_NULL, nullableBuilder.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNonNullIsNull test...");
        runTest();
    }
}

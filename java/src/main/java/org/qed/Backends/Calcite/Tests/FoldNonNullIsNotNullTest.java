package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNonNullIsNotNull;
import org.qed.RuleBuilder;

public class FoldNonNullIsNotNullTest {

    public static void runTest() {
        var rule = new FoldNonNullIsNotNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nonNullColumns(rule);
    }

    private static void nonNullColumns(FoldNonNullIsNotNull rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var nullableTable = RuleChecks.table(builder, 1, false, true);
        var plain = RuleChecks.ruleBuilder(builder);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(0)))
                .build();
        var after = plain.scan(table).filter(plain.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        var nullable = builder.scan(nullableTable)
                .filter(builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, nullable);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNonNullIsNotNull test...");
        runTest();
    }
}

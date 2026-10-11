package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNeFalse;
import org.qed.RuleBuilder;

public class FoldNeFalseTest {

    public static void runTest() {
        var rule = new FoldNeFalse();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldNeFalse rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var five = plain.literal(5);
        var before = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.NOT_EQUALS,
                        plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five),
                        plain.literal(false)))
                .build();
        var after = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.NOT_EQUALS,
                        plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five),
                        plain.literal(true)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNeFalse test...");
        runTest();
    }
}

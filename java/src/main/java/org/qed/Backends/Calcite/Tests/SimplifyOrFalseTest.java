package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyOrFalse;
import org.qed.RuleBuilder;

public class SimplifyOrFalseTest {

    public static void runTest() {
        var rule = new SimplifyOrFalse();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTable(rule);
    }

    private static void ordinaryTable(SimplifyOrFalse rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 1, false);
        var five = base.literal(5);
        var ten = base.literal(10);
        var plain = RuleChecks.ruleBuilder(base);
        var before = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.OR, RuleChecks.gt(plain, plain.field(0), 5),
                        plain.literal(false)))
                .build();
        var after = plain.scan(table).filter(RuleChecks.gt(plain, plain.field(0), 5)).build();
        RuleChecks.rewrites(rule, before, after);

        var noFalse = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.OR,
                        plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five),
                        plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(0), ten)))
                .build();
        RuleChecks.doesNotApply(rule, noFalse);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyOrFalse test...");
        runTest();
    }
}

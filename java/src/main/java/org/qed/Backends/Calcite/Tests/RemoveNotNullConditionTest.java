package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.RemoveNotNullCondition;
import org.qed.RuleBuilder;

public class RemoveNotNullConditionTest {

    public static void runTest() {
        var rule = new RemoveNotNullCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        notNullColumn(rule);
        nullableColumn(rule);
    }

    private static void notNullColumn(RemoveNotNullCondition rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 2, false, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(table)
                .filter(RuleChecks.gt(b, b.field(1), 5))
                .filter(b.call(SqlStdOperatorTable.IS_NOT_NULL, b.field(0)))
                .build();
        var after = b.scan(table)
                .filter(RuleChecks.gt(b, b.field(1), 5))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var wrongColumn = b.scan(table)
                .filter(RuleChecks.gt(b, b.field(0), 5))
                .filter(b.call(SqlStdOperatorTable.IS_NOT_NULL, b.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, wrongColumn);
    }

    private static void nullableColumn(RemoveNotNullCondition rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 2, false, true);
        var b = RuleChecks.ruleBuilder(base);
        var plan = b.scan(table)
                .filter(RuleChecks.gt(b, b.field(1), 5))
                .filter(b.call(SqlStdOperatorTable.IS_NOT_NULL, b.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveNotNullCondition test...");
        runTest();
    }
}

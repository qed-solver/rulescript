package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyFalseOr;
import org.qed.RuleBuilder;

public class SimplifyFalseOrTest {

    public static void runTest() {
        var rule = new SimplifyFalseOr();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTable(rule);
    }

    private static void ordinaryTable(SimplifyFalseOr rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var ten = builder.literal(10);
        var plain = RuleChecks.ruleBuilder(builder);
        var before = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.OR, plain.literal(false),
                        RuleChecks.gt(plain, plain.field(0), 5)))
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
        System.out.println("Running SimplifyFalseOr test...");
        runTest();
    }
}

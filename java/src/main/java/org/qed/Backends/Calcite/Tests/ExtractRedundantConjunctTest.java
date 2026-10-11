package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ExtractRedundantConjunct;
import org.qed.RuleBuilder;

public class ExtractRedundantConjunctTest {

    public static void runTest() {
        var rule = new ExtractRedundantConjunct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTable(rule);
    }

    private static void ordinaryTable(ExtractRedundantConjunct rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var ten = builder.literal(10);
        var seven = builder.literal(7);
        var one = builder.literal(1);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.OR,
                        builder.call(SqlStdOperatorTable.AND,
                                builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five),
                                builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(0), ten)),
                        builder.call(SqlStdOperatorTable.AND,
                                builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five),
                                builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), seven))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var a = RuleChecks.gt(plain, plain.field(0), 5);
        var after = plain.scan(table).filter(plain.and(
                        a,
                        plain.or(
                                plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(0), ten),
                                plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), seven))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.OR,
                        builder.call(SqlStdOperatorTable.AND,
                                builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five),
                                builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(0), ten)),
                        builder.call(SqlStdOperatorTable.AND,
                                builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), seven),
                                builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), one))))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running ExtractRedundantConjunct test...");
        runTest();
    }
}

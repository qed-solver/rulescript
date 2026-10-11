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
        var raw = RuleChecks.ruleBuilder(builder);
        var before = raw.scan(table)
                .filter(raw.call(SqlStdOperatorTable.OR,
                        raw.call(SqlStdOperatorTable.AND,
                                raw.call(SqlStdOperatorTable.GREATER_THAN, raw.field(0), raw.literal(5)),
                                raw.call(SqlStdOperatorTable.LESS_THAN, raw.field(0), raw.literal(10))),
                        raw.call(SqlStdOperatorTable.AND,
                                raw.call(SqlStdOperatorTable.GREATER_THAN, raw.field(0), raw.literal(5)),
                                raw.call(SqlStdOperatorTable.EQUALS, raw.field(0), raw.literal(7)))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.and(
                        RuleChecks.gt(plain, plain.field(0), 5),
                        plain.or(
                                plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(0), plain.literal(10)),
                                plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), plain.literal(7)))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = raw.scan(table)
                .filter(raw.call(SqlStdOperatorTable.OR,
                        raw.call(SqlStdOperatorTable.AND,
                                raw.call(SqlStdOperatorTable.GREATER_THAN, raw.field(0), raw.literal(5)),
                                raw.call(SqlStdOperatorTable.LESS_THAN, raw.field(0), raw.literal(10))),
                        raw.call(SqlStdOperatorTable.AND,
                                raw.call(SqlStdOperatorTable.EQUALS, raw.field(0), raw.literal(7)),
                                raw.call(SqlStdOperatorTable.GREATER_THAN, raw.field(0), raw.literal(1)))))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running ExtractRedundantConjunct test...");
        runTest();
    }
}

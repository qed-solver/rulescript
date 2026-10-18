package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.OptimizeIn;
import org.qed.RuleBuilder;

public final class OptimizeInTest {
    public static void runTest() {
        var rule = new OptimizeIn();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(OptimizeIn rule) {
        var schema = RuleBuilder.create();
        var table = RuleChecks.table(schema, 2, false);
        RelBuilder beforeBuilder = schema.transform(CalciteRuntime::configure);
        var before = beforeBuilder.scan(table).filter(beforeBuilder.call(SqlStdOperatorTable.OR,
                beforeBuilder.call(SqlStdOperatorTable.EQUALS, beforeBuilder.field(0), beforeBuilder.field(1)),
                beforeBuilder.call(SqlStdOperatorTable.EQUALS, beforeBuilder.field(0), beforeBuilder.field(1))))
                .build();

        RelBuilder afterBuilder = schema.transform(CalciteRuntime::configure);
        var after = afterBuilder.scan(table).filter(afterBuilder.call(SqlStdOperatorTable.EQUALS,
                afterBuilder.field(0), afterBuilder.field(1))).build();
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PruneFilters;
import org.qed.RuleBuilder;

public class PruneFiltersTest {
    public static void runTest() {
        var rule = new PruneFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneFilters rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        builder.scan(table);
        var input = builder.build();
        var rexBuilder = builder.getRexBuilder();
        var field = rexBuilder.makeInputRef(input, 0);
        var shared = rexBuilder.makeCall(SqlStdOperatorTable.IS_NULL, field);
        var innerOnly = rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_NULL, field);
        var outerOnly = rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN, field, rexBuilder.makeExactLiteral(java.math.BigDecimal.TEN));
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(
                org.apache.calcite.rel.logical.LogicalFilter.create(input,
                        rexBuilder.makeCall(SqlStdOperatorTable.AND, shared, innerOnly)),
                rexBuilder.makeCall(SqlStdOperatorTable.AND, shared, outerOnly));

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false);
        expectedBuilder.scan(expectedTable);
        var expectedInput = expectedBuilder.build();
        var expectedRexBuilder = expectedBuilder.getRexBuilder();
        var expectedField = expectedRexBuilder.makeInputRef(expectedInput, 0);
        var expected = org.apache.calcite.rel.logical.LogicalFilter.create(
                org.apache.calcite.rel.logical.LogicalFilter.create(expectedInput,
                        expectedRexBuilder.makeCall(SqlStdOperatorTable.AND,
                                expectedRexBuilder.makeCall(SqlStdOperatorTable.IS_NULL, expectedField),
                                expectedRexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_NULL, expectedField))),
                expectedRexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN, expectedField,
                        expectedRexBuilder.makeExactLiteral(java.math.BigDecimal.TEN)));
        RuleChecks.rewrites(rule, before, expected);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneFilters test...");
        runTest();
    }
}

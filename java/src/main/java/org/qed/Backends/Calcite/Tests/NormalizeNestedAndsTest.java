package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.NormalizeNestedAnds;
import org.qed.RuleBuilder;

public class NormalizeNestedAndsTest {
    public static void runTest() {
        var rule = new NormalizeNestedAnds();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NormalizeNestedAnds rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(table);
        var value = b.field(0);
        var low = b.call(SqlStdOperatorTable.GREATER_THAN, value, b.literal(0));
        var middle = b.call(SqlStdOperatorTable.LESS_THAN, value, b.literal(10));
        var high = b.call(SqlStdOperatorTable.NOT_EQUALS, value, b.literal(5));
        var before = b.filter(b.and(low, b.and(middle, high))).build();

        var expectedRuleBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedRuleBuilder, 1, false, false);
        var expectedScanBuilder = RuleChecks.ruleBuilder(expectedRuleBuilder);
        expectedScanBuilder.scan(expectedTable);
        var expectedValue = expectedScanBuilder.field(0);
        var expectedLow = expectedScanBuilder.call(SqlStdOperatorTable.GREATER_THAN, expectedValue,
                expectedScanBuilder.literal(0));
        var expectedMiddle = expectedScanBuilder.call(SqlStdOperatorTable.LESS_THAN, expectedValue,
                expectedScanBuilder.literal(10));
        var expectedHigh = expectedScanBuilder.call(SqlStdOperatorTable.NOT_EQUALS, expectedValue,
                expectedScanBuilder.literal(5));
        var expected = expectedScanBuilder
                .filter(expectedScanBuilder.and(expectedScanBuilder.and(expectedLow, expectedMiddle), expectedHigh))
                .build();
        RuleChecks.rewrites(rule, before, expected);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}

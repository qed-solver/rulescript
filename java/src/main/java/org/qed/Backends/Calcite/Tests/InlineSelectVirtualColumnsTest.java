package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.InlineSelectVirtualColumns;
import org.qed.RuleBuilder;

import java.util.List;

public final class InlineSelectVirtualColumnsTest {
    private InlineSelectVirtualColumnsTest() {}

    public static void runTest() {
        var rule = new InlineSelectVirtualColumns();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositiveAndExpected();
        handBuiltNoMatch();
    }

    private static void handBuiltPositiveAndExpected() {
        var sourceBuilder = RuleBuilder.create();
        var table = RuleChecks.table(sourceBuilder, 2, false);
        var builder = RuleChecks.ruleBuilder(sourceBuilder);
        builder.scan(table);
        var inputFields = builder.fields();
        var f = builder.call(SqlStdOperatorTable.ABS, inputFields.get(0));
        var g = builder.call(SqlStdOperatorTable.PLUS, inputFields.get(1), builder.literal(3));
        builder.project(List.of(f, g, inputFields.get(0), inputFields.get(1)), List.of(), true);
        var projectFields = builder.fields();
        var virtualPredicate = RuleChecks.gt(builder, projectFields.get(0), 5);
        var ordinaryPredicate = RuleChecks.eq(builder, projectFields.get(1), builder.literal(10));
        var before = LogicalFilter.create(builder.build(), builder.call(SqlStdOperatorTable.AND, virtualPredicate, ordinaryPredicate));

        var expectedRuleBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedRuleBuilder, 2, false);
        var expectedInput = RuleChecks.ruleBuilder(expectedRuleBuilder);
        expectedInput.scan(expectedTable);
        var expectedSource = expectedInput.fields();
        var expectedInline = RuleChecks.gt(expectedInput, expectedInput.call(SqlStdOperatorTable.ABS, expectedSource.get(0)), 5);
        expectedInput.filter(expectedInline);
        var expectedFilteredScan = expectedInput.build();
        expectedInput.push(expectedFilteredScan).project(List.of(
                expectedInput.call(SqlStdOperatorTable.ABS, expectedSource.get(0)),
                expectedInput.call(SqlStdOperatorTable.PLUS, expectedSource.get(1), expectedInput.literal(3)),
                expectedSource.get(0), expectedSource.get(1)), List.of(), true);
        var expectedFields = expectedInput.fields();
        var expectedAbove = RuleChecks.eq(expectedInput, expectedFields.get(1), expectedInput.literal(10));
        var expected = LogicalFilter.create(expectedInput.build(), expectedAbove);
        RuleChecks.rewrites(new InlineSelectVirtualColumns(), before, expected);
    }

    private static void handBuiltNoMatch() {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 2, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        builder.scan(table);
        var fields = builder.fields();
        var f = builder.call(SqlStdOperatorTable.ABS, fields.get(0));
        var g = builder.call(SqlStdOperatorTable.PLUS, fields.get(1), builder.literal(3));
        builder.project(List.of(f, g, fields.get(0), fields.get(1)), List.of(), true);
        var above = builder.fields();
        var rexBuilder = builder.getRexBuilder();
        var innerCondition = rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN, above.get(0), rexBuilder.makeExactLiteral(java.math.BigDecimal.valueOf(5)));
        var outerCondition = rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, above.get(1), rexBuilder.makeExactLiteral(java.math.BigDecimal.TEN));
        var project = builder.build();
        RuleChecks.doesNotApply(new InlineSelectVirtualColumns(), LogicalFilter.create(
                LogicalFilter.create(project, innerCondition), outerCondition));
    }

    public static void main(String[] args) {
        System.out.println("Running InlineSelectVirtualColumns test...");
        runTest();
    }
}

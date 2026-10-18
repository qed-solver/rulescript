package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.FilterMerge;
import org.qed.RuleBuilder;

public final class FilterMergeTest {
    public static void runTest() {
        var rule = new FilterMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        mergesHandBuiltFilters();
        rejectsDifferentFilterInputShape();
    }

    private static void mergesHandBuiltFilters() {
        var schema = RuleBuilder.create();
        var source = RuleChecks.table(schema, 1, false);
        var b = builder(schema);
        var bottomInput = b.scan(source).build();
        var x = b.getRexBuilder().makeInputRef(bottomInput, 0);
        var low = RuleChecks.gt(b, x, 11);
        var bottom = LogicalFilter.create(bottomInput, low);
        var y = b.getRexBuilder().makeInputRef(bottom, 0);
        var high = RuleChecks.gt(b, y, 37);
        var before = LogicalFilter.create(bottom, high);
        var expectedInput = builder(schema).scan(source).build();
        var expectedX = b.getRexBuilder().makeInputRef(expectedInput, 0);
        var expected = LogicalFilter.create(expectedInput, b.call(SqlStdOperatorTable.AND,
                RuleChecks.gt(b, expectedX, 37), RuleChecks.gt(b, expectedX, 11)));
        RuleChecks.rewrites(rule(), before, expected);
    }

    private static void rejectsDifferentFilterInputShape() {
        var schema = RuleBuilder.create();
        var source = RuleChecks.table(schema, 2, false);
        var b = builder(schema);
        var input = b.scan(source).build();
        var x = b.getRexBuilder().makeInputRef(input, 0);
        var inner = LogicalFilter.create(input, RuleChecks.gt(b, x, 11));
        var y = b.getRexBuilder().makeInputRef(inner, 0);
        var outer = LogicalFilter.create(inner, RuleChecks.gt(b, y, 37));
        RuleChecks.doesNotApply(rule(), outer);
    }

    private static FilterMerge rule() {
        return new FilterMerge();
    }

    private static RelBuilder builder(RuleBuilder schema) {
        return RuleChecks.ruleBuilder(schema);
    }

    public static void main(String[] args) {
        System.out.println("Running FilterMerge test...");
        runTest();
    }
}

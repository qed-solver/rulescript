package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifySelectFilters;
import org.qed.RuleBuilder;

public class SimplifySelectFiltersTest {
    public static void runTest() {
        var rule = new SimplifySelectFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltNullablePredicate();
        rejectsDifferentOperator();
    }

    private static void handBuiltNullablePredicate() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(table);
        var input = b.field(0);
        var scan = b.build();
        var pred = RuleChecks.gt(b, input, 0);
        var nullBool = b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.BOOLEAN));
        var source = org.apache.calcite.rel.logical.LogicalFilter.create(scan,
                b.getRexBuilder().makeCall(SqlStdOperatorTable.OR, pred, nullBool));
        var expected = org.apache.calcite.rel.logical.LogicalFilter.create(scan, pred);
        RuleChecks.rewrites(new SimplifySelectFilters(), source, expected);
    }

    private static void rejectsDifferentOperator() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(table);
        var pred = RuleChecks.gt(b, b.field(0), 0);
        var scan = b.build();
        var isNull = b.call(SqlStdOperatorTable.IS_NULL, pred);
        var source = org.apache.calcite.rel.logical.LogicalFilter.create(scan, isNull);
        RuleChecks.doesNotApply(new SimplifySelectFilters(), source);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifySelectFilters test...");
        runTest();
        if (CalciteTester.failures() != 0) throw new AssertionError("Calcite test failures: " + CalciteTester.failures());
    }
}

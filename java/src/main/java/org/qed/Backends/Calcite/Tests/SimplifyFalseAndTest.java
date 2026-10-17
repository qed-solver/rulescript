package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyFalseAnd;
import org.qed.RuleBuilder;

public final class SimplifyFalseAndTest {
    public static void runTest() {
        var rule = new SimplifyFalseAnd();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        independentConcreteFilter();
        doesNotMatchOr();
    }

    private static void independentConcreteFilter() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        var scan = builder.scan(table).build();
        var rex = builder.transform(CalciteRuntime::configure).getRexBuilder();
        var input = rex.makeInputRef(scan.getRowType().getFieldList().get(0).getType(), 0);
        var rhs = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, input, rex.makeExactLiteral(java.math.BigDecimal.valueOf(5)));
        var condition = rex.makeCall(SqlStdOperatorTable.AND, java.util.List.of(
                rex.makeCall(SqlStdOperatorTable.AND, java.util.List.of(rex.makeLiteral(false))), rhs));
        var before = LogicalFilter.create(scan, condition);

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false, true);
        var expectedScan = expectedBuilder.scan(expectedTable).build();
        var expectedRex = expectedBuilder.transform(CalciteRuntime::configure).getRexBuilder();
        var expected = LogicalFilter.create(expectedScan, expectedRex.makeLiteral(false));
        RuleChecks.rewrites(new SimplifyFalseAnd(), before, expected);
    }

    private static void doesNotMatchOr() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var scan = builder.scan(table).build();
        var rex = builder.transform(CalciteRuntime::configure).getRexBuilder();
        var input = rex.makeInputRef(scan.getRowType().getFieldList().get(0).getType(), 0);
        var rhs = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, input, rex.makeExactLiteral(java.math.BigDecimal.valueOf(5)));
        var condition = rex.makeCall(SqlStdOperatorTable.OR, rex.makeLiteral(false), rhs);
        var plan = LogicalFilter.create(scan, condition);
        RuleChecks.doesNotApply(new SimplifyFalseAnd(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyFalseAnd test...");
        runTest();
    }
}

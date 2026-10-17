package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FilterProjectTranspose;
import org.qed.RuleBuilder;

public final class FilterProjectTransposeTest {
    public static void runTest() {
        var rule = new FilterProjectTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesComputedProjection();
        rejectsFilterOnSecondProjectedColumn();
    }

    private static void rewritesComputedProjection() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        RelBuilder b = RuleChecks.ruleBuilder(builder);
        var input = b.scan(table).build();
        var value = b.getRexBuilder().makeCall(SqlStdOperatorTable.PLUS,
                b.getRexBuilder().makeInputRef(input, 0), b.literal(1));
        var source = b.push(input).project(value).filter(b.call(SqlStdOperatorTable.GREATER_THAN, b.field(0), b.literal(10))).build();

        var expected = b.push(input).filter(b.call(SqlStdOperatorTable.GREATER_THAN, value, b.literal(10))).project(value).build();
        RuleChecks.rewrites(new FilterProjectTranspose(), source, expected);
    }

    private static void rejectsFilterOnSecondProjectedColumn() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        RelBuilder b = RuleChecks.ruleBuilder(builder);
        var input = b.scan(table).build();
        var source = b.push(input).project(b.field(0), b.field(1))
                .filter(b.call(SqlStdOperatorTable.GREATER_THAN, b.field(1), b.literal(10))).build();
        RuleChecks.doesNotApply(new FilterProjectTranspose(), source);
    }

    public static void main(String[] args) {
        System.out.println("Running FilterProjectTranspose test...");
        runTest();
    }
}

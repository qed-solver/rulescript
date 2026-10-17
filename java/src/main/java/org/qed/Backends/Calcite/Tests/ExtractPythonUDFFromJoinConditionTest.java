package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.ExtractPythonUDFFromJoinCondition;
import org.qed.RuleBuilder;

public final class ExtractPythonUDFFromJoinConditionTest {
    public static void runTest() {
        var rule = new ExtractPythonUDFFromJoinCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltJoinConditionIsSplit(rule);
        handBuiltLeftJoinDoesNotMatch(rule);
    }

    private static void handBuiltJoinConditionIsSplit(ExtractPythonUDFFromJoinCondition rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var rb = RuleChecks.ruleBuilder(builder);
        rb.scan(left).scan(right);
        var udf = rb.call(builder.genericPredicateOp("python_udf", true), rb.field(2, 0, 0));
        var rest = rb.call(SqlStdOperatorTable.IS_NOT_NULL, rb.field(2, 0, 0));
        var condition = rb.call(SqlStdOperatorTable.AND, udf, rest);
        var before = rb.join(JoinRelType.INNER, condition).build();

        var expectedBuilder = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedBuilder, 1, false);
        var expectedRight = RuleChecks.table(expectedBuilder, 1, false);
        var eb = RuleChecks.ruleBuilder(expectedBuilder);
        eb.scan(expectedLeft).scan(expectedRight);
        var expectedRest = eb.call(SqlStdOperatorTable.IS_NOT_NULL, eb.field(2, 0, 0));
        var expectedUdf = eb.call(expectedBuilder.genericPredicateOp("python_udf", true), eb.field(2, 0, 0));
        var join = eb.join(JoinRelType.INNER, expectedRest).build();
        var expected = LogicalFilter.create(join, expectedUdf);
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void handBuiltLeftJoinDoesNotMatch(ExtractPythonUDFFromJoinCondition rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var rb = RuleChecks.ruleBuilder(builder);
        rb.scan(left).scan(right);
        var udf = rb.call(builder.genericPredicateOp("python_udf", true), rb.field(2, 0, 0));
        var rest = rb.call(SqlStdOperatorTable.IS_NOT_NULL, rb.field(2, 0, 0));
        var condition = rb.call(SqlStdOperatorTable.AND, udf, rest);
        var plan = rb.join(JoinRelType.LEFT, condition).build();
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running ExtractPythonUDFFromJoinCondition test...");
        runTest();
    }
}

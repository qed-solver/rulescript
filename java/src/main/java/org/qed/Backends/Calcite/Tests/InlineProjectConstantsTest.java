package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.InlineProjectConstants;
import org.qed.RuleBuilder;

public final class InlineProjectConstantsTest {
    public static void runTest() {
        var rule = new InlineProjectConstants();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsHandBuiltNearMisses();
    }

    private static void rewritesHandBuiltPlan() {
        var builder = RuleBuilder.create();
        var source = RuleChecks.table(builder, 1, false);
        var constant = RuleChecks.table(builder, 1, true, false);
        var inputBuilder = RuleChecks.ruleBuilder(builder);
        RelNode input = inputBuilder.scan(source).scan(constant)
                .join(JoinRelType.INNER, RuleChecks.eq(inputBuilder,
                        CalciteRuntime.field(inputBuilder, 2, 0), CalciteRuntime.field(inputBuilder, 2, 1)))
                .project(inputBuilder.call(SqlStdOperatorTable.ABS, CalciteRuntime.field(inputBuilder, 1, 0)))
                .build();
        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        RelNode expected = expectedBuilder.scan(source).scan(constant)
                .join(JoinRelType.INNER, RuleChecks.eq(expectedBuilder,
                        CalciteRuntime.field(expectedBuilder, 2, 0), CalciteRuntime.field(expectedBuilder, 2, 1)))
                .project(expectedBuilder.call(SqlStdOperatorTable.ABS, CalciteRuntime.field(expectedBuilder, 1, 1)))
                .build();
        RuleChecks.rewrites(new InlineProjectConstants(), input, expected);
    }

    private static void rejectsHandBuiltNearMisses() {
        var builder = RuleBuilder.create();
        var source = RuleChecks.table(builder, 1, false);
        var constant = RuleChecks.table(builder, 1, true, false);
        var duplicate = RuleChecks.table(builder, 1, false, false);
        var nonEqualityBuilder = RuleChecks.ruleBuilder(builder);
        var noEquality = nonEqualityBuilder.scan(source).scan(constant)
                .join(JoinRelType.INNER, nonEqualityBuilder.literal(true))
                .project(nonEqualityBuilder.call(SqlStdOperatorTable.ABS,
                        CalciteRuntime.field(nonEqualityBuilder, 1, 0))).build();
        RuleChecks.doesNotApply(new InlineProjectConstants(), noEquality);
        var nonUniqueBuilder = RuleChecks.ruleBuilder(builder);
        var nonUnique = nonUniqueBuilder.scan(source).scan(duplicate)
                .join(JoinRelType.INNER, RuleChecks.eq(nonUniqueBuilder,
                        CalciteRuntime.field(nonUniqueBuilder, 2, 0), CalciteRuntime.field(nonUniqueBuilder, 2, 1)))
                .project(nonUniqueBuilder.call(SqlStdOperatorTable.ABS,
                        CalciteRuntime.field(nonUniqueBuilder, 1, 0))).build();
        RuleChecks.doesNotApply(new InlineProjectConstants(), nonUnique);
    }

    public static void main(String[] args) {
        System.out.println("Running InlineProjectConstants test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.TryDecorrelateProject;
import org.qed.RuleBuilder;

import java.util.List;

public final class TryDecorrelateProjectTest {
    public static void runTest() {
        var rule = new TryDecorrelateProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsOuterJoin();
    }

    private static void rewritesHandBuiltPlan() {
        var schemaBuilder = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(schemaBuilder);
        var left = RuleChecks.table(schemaBuilder, 1, false);
        var right = RuleChecks.table(schemaBuilder, 2, false);
        SqlOperator on = schemaBuilder.genericPredicateOp("on", true);

        var before = builder.scan(left).scan(right)
                .project(List.of(builder.field(1), builder.field(0)), List.of(), true)
                .join(JoinRelType.INNER, builder.call(on, builder.field(2, 0, 0), builder.field(2, 1, 0),
                        builder.field(2, 1, 1))).build();

        var expectedSchema = RuleBuilder.create();
        var expectedBuilder = RuleChecks.ruleBuilder(expectedSchema);
        RuleChecks.table(expectedSchema, 1, false);
        RuleChecks.table(expectedSchema, 2, false);
        var expectedJoin = expectedBuilder.scan(left).scan(right).join(JoinRelType.INNER, expectedBuilder.literal(true))
                .project(List.of(expectedBuilder.field(0), expectedBuilder.field(2), expectedBuilder.field(1)), List.of(), true)
                .filter(expectedBuilder.call(on, expectedBuilder.field(0), expectedBuilder.field(1), expectedBuilder.field(2))).build();
        RuleChecks.rewrites(new TryDecorrelateProject(), before, expectedJoin);
    }

    private static void rejectsOuterJoin() {
        var schemaBuilder = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(schemaBuilder);
        var left = RuleChecks.table(schemaBuilder, 1, false);
        var right = RuleChecks.table(schemaBuilder, 2, false);
        SqlOperator on = schemaBuilder.genericPredicateOp("on", true);
        var plan = builder.scan(left).scan(right)
                .project(List.of(builder.field(1), builder.field(0)), List.of(), true)
                .join(JoinRelType.LEFT, builder.call(on, builder.field(2, 0, 0), builder.field(2, 1, 0),
                        builder.field(2, 1, 1))).build();
        RuleChecks.doesNotApply(new TryDecorrelateProject(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateProject test...");
        runTest();
    }
}

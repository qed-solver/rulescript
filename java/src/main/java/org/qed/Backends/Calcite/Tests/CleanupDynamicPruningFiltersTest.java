package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.CleanupDynamicPruningFilters;
import org.qed.RuleBuilder;

public class CleanupDynamicPruningFiltersTest {
    public static void runTest() {
        var rule = new CleanupDynamicPruningFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        var builder = RuleBuilder.create();
        var left = table(builder, 1, false);
        var right = table(builder, 1, false);
        var input = ruleBuilder(builder);
        var dpp = builder.genericPredicateOp("dpp_hand", true);
        var extra = builder.genericPredicateOp("extra_hand", true);
        var before = input.scan(left).filter(call(input, dpp, input.field(0)))
                .scan(right).join(JoinRelType.INNER, input.and(call(input, dpp, leftJoinField(input)),
                        call(input, extra, leftJoinField(input), rightJoinField(input)))).build();
        var expectedBuilder = ruleBuilder(builder);
        var expectedDpp = builder.genericPredicateOp("dpp_hand", true);
        var expectedExtra = builder.genericPredicateOp("extra_hand", true);
        var expected = expectedBuilder.scan(left).scan(right)
                .join(JoinRelType.INNER, expectedBuilder.and(
                        call(expectedBuilder, expectedDpp, leftJoinField(expectedBuilder)),
                        call(expectedBuilder, expectedExtra, leftJoinField(expectedBuilder),
                                rightJoinField(expectedBuilder)))).build();
        rewrites(rule, before, expected);

        var nearBuilder = ruleBuilder(builder);
        var nearDpp = builder.genericPredicateOp("dpp_hand", true);
        var nearExtra = builder.genericPredicateOp("extra_hand", true);
        var nearMiss = nearBuilder.scan(left).filter(call(nearBuilder, nearDpp, nearBuilder.field(0)))
                .scan(right).join(JoinRelType.LEFT, nearBuilder.and(
                        call(nearBuilder, nearDpp, leftJoinField(nearBuilder)),
                        call(nearBuilder, nearExtra, leftJoinField(nearBuilder), rightJoinField(nearBuilder)))).build();
        doesNotApply(rule, nearMiss);
    }

    private static String table(RuleBuilder builder, int columns, boolean unique) {
        return RuleChecks.table(builder, columns, unique);
    }

    private static RelBuilder ruleBuilder(RuleBuilder builder) {
        return builder.transform(CalciteRuntime::configure);
    }

    private static RexNode call(RelBuilder builder, SqlOperator operator, RexNode... args) {
        return builder.call(operator, args);
    }

    private static RexNode leftJoinField(RelBuilder builder) {
        return builder.getRexBuilder().makeInputRef(builder.peek(1).getRowType().getFieldList().get(0).getType(), 0);
    }

    private static RexNode rightJoinField(RelBuilder builder) {
        return builder.getRexBuilder().makeInputRef(builder.peek(0).getRowType().getFieldList().get(0).getType(), 1);
    }

    private static void rewrites(CleanupDynamicPruningFilters rule, RelNode before, RelNode after) {
        RuleChecks.rewrites(rule, before, after);
    }

    private static void doesNotApply(CleanupDynamicPruningFilters rule, RelNode plan) {
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running CleanupDynamicPruningFilters test...");
        runTest();
    }
}

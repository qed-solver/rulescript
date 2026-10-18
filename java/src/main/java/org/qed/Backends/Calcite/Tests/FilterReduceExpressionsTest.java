package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRule;
import org.qed.RRuleInstances.FilterReduceExpressions;
import org.qed.RuleBuilder;

public final class FilterReduceExpressionsTest {
    public static void runTest() {
        RRule rule = new FilterReduceExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltFalseFilterRewrites();
        nonFalseFilterDoesNotMatch();
    }

    private static void handBuiltFalseFilterRewrites() {
        var qedBuilder = RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        RelNode input = builder.scan(table).build();
        RelNode before = LogicalFilter.create(input, builder.getRexBuilder().makeLiteral(false));
        RelNode expected = builder.push(input).empty().build();
        RuleChecks.rewrites(new FilterReduceExpressions(), before, expected);
    }

    private static void nonFalseFilterDoesNotMatch() {
        var qedBuilder = RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        RelNode plan = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), builder.literal(5)))
                .build();
        RuleChecks.doesNotApply(new FilterReduceExpressions(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running FilterReduceExpressions test...");
        runTest();
    }
}

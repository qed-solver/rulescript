package org.qed.Backends.Calcite.Tests;

import com.google.common.collect.ImmutableSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRule;
import org.qed.RRuleInstances.NormalizeSelectNotAnyFilter;
import org.qed.RuleBuilder;

public class NormalizeSelectNotAnyFilterTest {
    public static void runTest() {
        var rule = new NormalizeSelectNotAnyFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var outerName = RuleChecks.table(builder, 1, false);
        var subName = RuleChecks.table(builder, 1, false);
        RelBuilder relBuilder = builder.transform(CalciteRuntime::configure);
        RelNode outer = relBuilder.scan(outerName).build();
        RelNode sub = relBuilder.scan(subName).build();
        RexNode in = relBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT,
                relBuilder.in(sub, java.util.List.of(relBuilder.getRexBuilder().makeInputRef(outer, 0))));
        RelNode before = LogicalFilter.create(outer, in);

        var rb = relBuilder.getRexBuilder();
        var id = relBuilder.getCluster().createCorrel();
        var corr = rb.makeCorrel(outer.getRowType(), id);
        RexNode equality = rb.makeCall(SqlStdOperatorTable.EQUALS, rb.makeFieldAccess(corr, 0),
                rb.makeInputRef(sub.getRowType().getFieldList().get(0).getType(), 0));
        RexNode notFalse = rb.makeCall(SqlStdOperatorTable.OR, equality,
                rb.makeCall(SqlStdOperatorTable.IS_NULL, equality));
        RexNode notExists = rb.makeCall(SqlStdOperatorTable.NOT,
                RexSubQuery.exists(LogicalFilter.create(sub, notFalse)));
        RelNode expected = LogicalFilter.create(outer, notExists, ImmutableSet.of(id));
        RuleChecks.rewrites(rule, before, expected);

        var missRuleBuilder = RuleBuilder.create();
        var leftName = RuleChecks.table(missRuleBuilder, 2, false);
        var innerName = RuleChecks.table(missRuleBuilder, 1, false);
        RelBuilder missBuilder = missRuleBuilder.transform(CalciteRuntime::configure);
        RelNode left = missBuilder.scan(leftName).build();
        RelNode inner = missBuilder.scan(innerName).build();
        RexNode secondColumn = missBuilder.getRexBuilder().makeInputRef(left, 1);
        RelNode wrongIndex = LogicalFilter.create(left, missBuilder.getRexBuilder().makeCall(
                SqlStdOperatorTable.NOT, missBuilder.in(inner, java.util.List.of(secondColumn))));
        RuleChecks.doesNotApply(rule, wrongIndex);
    }

    public static void main(String[] args) {
        System.out.println("Running NormalizeSelectNotAnyFilter test...");
        runTest();
    }
}

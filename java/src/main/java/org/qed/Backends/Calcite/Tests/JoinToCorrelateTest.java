package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalCorrelate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.JoinToCorrelate;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinToCorrelateTest {
    public static void runTest() {
        var rule = new JoinToCorrelate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinToCorrelate rule) {
        var source = RuleBuilder.create();
        var left = RuleChecks.table(source, 1, false);
        var right = RuleChecks.table(source, 1, false);
        RelBuilder beforeBuilder = RuleChecks.ruleBuilder(source);
        var beforeLeft = beforeBuilder.scan(left).build();
        var beforeRight = beforeBuilder.scan(right).build();
        beforeBuilder.push(beforeLeft).push(beforeRight);
        var beforeCondition = beforeBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.GREATER_THAN,
                beforeBuilder.getRexBuilder().makeInputRef(beforeLeft.getRowType().getFieldList().get(0).getType(), 0),
                beforeBuilder.getRexBuilder().makeInputRef(beforeRight.getRowType().getFieldList().get(0).getType(), 1));
        var before = beforeBuilder.join(JoinRelType.INNER, beforeCondition).build();

        var expectedBuilder = RuleChecks.ruleBuilder(source);
        var expectedLeft = expectedBuilder.scan(left).build();
        var expectedRight = expectedBuilder.scan(right).build();
        var id = expectedBuilder.getCluster().createCorrel();
        var correlation = expectedBuilder.getRexBuilder().makeCorrel(expectedLeft.getRowType(), id);
        var leftField = expectedBuilder.getRexBuilder().makeFieldAccess(correlation, 0);
        var rightField = expectedBuilder.getRexBuilder().makeInputRef(
                expectedRight.getRowType().getFieldList().get(0).getType(), 0);
        var condition = expectedBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.GREATER_THAN,
                leftField, rightField);
        var expected = LogicalCorrelate.create(expectedLeft,
                LogicalFilter.create(expectedRight, condition), List.of(), id,
                ImmutableBitSet.range(expectedLeft.getRowType().getFieldCount()), JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, expected);

        var nearMissBuilder = RuleChecks.ruleBuilder(source);
        var nearMissLeft = nearMissBuilder.scan(left).build();
        var nearMissRight = nearMissBuilder.scan(right).build();
        nearMissBuilder.push(nearMissLeft).push(nearMissRight);
        var nearMissCondition = nearMissBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.GREATER_THAN,
                nearMissBuilder.getRexBuilder().makeInputRef(nearMissLeft.getRowType().getFieldList().get(0).getType(), 0),
                nearMissBuilder.getRexBuilder().makeInputRef(nearMissRight.getRowType().getFieldList().get(0).getType(), 1));
        var nearMiss = nearMissBuilder.join(JoinRelType.LEFT, nearMissCondition).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}

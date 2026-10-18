package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalMinus;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.MinusToAntiJoin;

public class MinusToAntiJoinTest {
    public static void runTest() {
        var rule = new MinusToAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MinusToAntiJoin rule) {
        var source = org.qed.RuleBuilder.create();
        var left = RuleChecks.table(source, 1, false);
        var right = RuleChecks.table(source, 1, false);
        var leftScan = source.scan(left).build();
        var rightScan = source.scan(right).build();
        var before = LogicalMinus.create(java.util.List.of(leftScan, rightScan), false);

        var expectedBuilder = org.qed.RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedBuilder, 1, false);
        var expectedRight = RuleChecks.table(expectedBuilder, 1, false);
        expectedBuilder.scan(expectedLeft).scan(expectedRight);
        var expectedRightScan = expectedBuilder.peek();
        var expectedLeftScan = expectedBuilder.peek(2, 0);
        var expectedRelBuilder = RuleChecks.ruleBuilder(expectedBuilder);
        var rexBuilder = expectedRelBuilder.getRexBuilder();
        var condition = rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                rexBuilder.makeInputRef(expectedLeftScan.getRowType().getFieldList().get(0).getType(), 0),
                rexBuilder.makeInputRef(expectedRightScan.getRowType().getFieldList().get(0).getType(), 1));
        var expectedInput = LogicalJoin.create(expectedLeftScan, expectedRightScan, java.util.List.of(), condition,
                java.util.Set.of(), JoinRelType.ANTI);
        var after = LogicalAggregate.create(expectedInput, java.util.List.of(), ImmutableBitSet.of(0), null,
                java.util.List.of());
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running MinusToAntiJoin test...");
        runTest();
    }
}

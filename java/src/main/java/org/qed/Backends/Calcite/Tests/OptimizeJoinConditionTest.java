package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.OptimizeJoinCondition;
import org.qed.RuleBuilder;

public class OptimizeJoinConditionTest {
    public static void runTest() {
        var rule = new OptimizeJoinCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(OptimizeJoinCondition rule) {
        var sourceCatalog = RuleBuilder.create();
        var leftName = RuleChecks.table(sourceCatalog, 1, false);
        var rightName = RuleChecks.table(sourceCatalog, 1, false);
        var builder = RuleChecks.ruleBuilder(sourceCatalog);
        var left = scan(builder, leftName);
        var right = scan(builder, rightName);
        var x = builder.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var y = builder.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        var condition = or(builder, eq(builder, x, y), and(builder, isNull(builder, x), isNull(builder, y)));
        RelNode before = LogicalJoin.create(left, right, java.util.List.of(), condition, java.util.Set.of(), JoinRelType.INNER);

        var expectedCatalog = RuleBuilder.create();
        RuleChecks.table(expectedCatalog, 1, false);
        RuleChecks.table(expectedCatalog, 1, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedCatalog);
        var expectedLeft = scan(expectedBuilder, leftName);
        var expectedRight = scan(expectedBuilder, rightName);
        var expectedX = expectedBuilder.getRexBuilder().makeInputRef(expectedLeft.getRowType().getFieldList().get(0).getType(), 0);
        var expectedY = expectedBuilder.getRexBuilder().makeInputRef(expectedRight.getRowType().getFieldList().get(0).getType(), 1);
        var expectedCondition = expectedBuilder.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, expectedX, expectedY);
        RelNode after = LogicalJoin.create(expectedLeft, expectedRight, java.util.List.of(), expectedCondition,
                java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, after);

        var nearMissCatalog = RuleBuilder.create();
        RuleChecks.table(nearMissCatalog, 1, false);
        RuleChecks.table(nearMissCatalog, 1, false);
        var nearMissBuilder = RuleChecks.ruleBuilder(nearMissCatalog);
        var nearLeft = scan(nearMissBuilder, leftName);
        var nearRight = scan(nearMissBuilder, rightName);
        var nearX = nearMissBuilder.getRexBuilder().makeInputRef(nearLeft.getRowType().getFieldList().get(0).getType(), 0);
        var nearY = nearMissBuilder.getRexBuilder().makeInputRef(nearRight.getRowType().getFieldList().get(0).getType(), 1);
        var reversed = or(nearMissBuilder, eq(nearMissBuilder, nearX, nearY),
                and(nearMissBuilder, isNull(nearMissBuilder, nearY), isNull(nearMissBuilder, nearX)));
        RelNode nearMiss = LogicalJoin.create(nearLeft, nearRight, java.util.List.of(), reversed,
                java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RelNode scan(RelBuilder builder, String table) {
        return builder.scan(table).build();
    }

    private static RexNode eq(RelBuilder b, RexNode x, RexNode y) {
        return b.call(SqlStdOperatorTable.EQUALS, x, y);
    }

    private static RexNode isNull(RelBuilder b, RexNode x) {
        return b.call(SqlStdOperatorTable.IS_NULL, x);
    }

    private static RexNode and(RelBuilder b, RexNode x, RexNode y) {
        return b.call(SqlStdOperatorTable.AND, x, y);
    }

    private static RexNode or(RelBuilder b, RexNode x, RexNode y) {
        return b.call(SqlStdOperatorTable.OR, x, y);
    }

    public static void main(String[] args) {
        System.out.println("Running OptimizeJoinCondition test...");
        runTest();
    }
}

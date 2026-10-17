package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PushLeakproofJoinIntoPermeableBarrierLeft;
import org.qed.RuleBuilder;

public final class PushLeakproofJoinIntoPermeableBarrierLeftTest {
    public static void runTest() {
        var rule = new PushLeakproofJoinIntoPermeableBarrierLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushLeakproofJoinIntoPermeableBarrierLeft rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 2, false);
        var right = RuleChecks.table(builder, 2, false);
        RelNode leftScan = builder.scan(left).build();
        RelNode barrier = LogicalProject.create(leftScan,
                java.util.List.of(), java.util.List.of(builder.getRexBuilder().makeInputRef(leftScan, 0),
                        builder.getRexBuilder().makeInputRef(leftScan, 1)), java.util.List.of("c0", "c1"));
        var planBuilder = RuleChecks.ruleBuilder(builder);
        RelNode rightScan = planBuilder.scan(right).build();
        var condition = planBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.LESS_THAN,
                planBuilder.getRexBuilder().makeInputRef(barrier, 0),
                planBuilder.getRexBuilder().makeInputRef(rightScan.getRowType().getFieldList().get(0).getType(), 2));
        RelNode plan = org.apache.calcite.rel.logical.LogicalJoin.create(barrier, rightScan,
                java.util.List.of(), condition, java.util.Set.of(), JoinRelType.INNER);
        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        RelNode expectedLeft = expectedBuilder.scan(left).build();
        RelNode expectedRight = expectedBuilder.scan(right).build();
        var expectedCondition = expectedBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.LESS_THAN,
                expectedBuilder.getRexBuilder().makeInputRef(expectedLeft, 0),
                expectedBuilder.getRexBuilder().makeInputRef(expectedRight.getRowType().getFieldList().get(0).getType(), 2));
        RelNode expectedJoin = org.apache.calcite.rel.logical.LogicalJoin.create(expectedLeft, expectedRight,
                java.util.List.of(), expectedCondition, java.util.Set.of(), JoinRelType.INNER);
        var rex = expectedBuilder.getRexBuilder();
        RelNode expected = LogicalProject.create(expectedJoin, java.util.List.of(), java.util.List.of(
                rex.makeCast(expectedJoin.getRowType().getFieldList().get(0).getType(), rex.makeInputRef(expectedJoin, 0)),
                rex.makeCast(expectedJoin.getRowType().getFieldList().get(1).getType(), rex.makeInputRef(expectedJoin, 1)),
                rex.makeCast(expectedJoin.getRowType().getFieldList().get(2).getType(), rex.makeInputRef(expectedJoin, 2)),
                rex.makeCast(expectedJoin.getRowType().getFieldList().get(3).getType(), rex.makeInputRef(expectedJoin, 3))),
                java.util.List.of("c0", "c1", "Table_1_Column_0", "Table_1_Column_1"));
        RuleChecks.rewrites(rule, plan, expected);

        var missBuilder = RuleChecks.ruleBuilder(builder);
        RelNode missLeft = missBuilder.scan(left).build();
        RelNode missBarrier = LogicalProject.create(missLeft, java.util.List.of(),
                java.util.List.of(missBuilder.getRexBuilder().makeInputRef(missLeft, 0),
                        missBuilder.getRexBuilder().makeInputRef(missLeft, 1)),
                java.util.List.of("c0", "c1"));
        RelNode missRight = missBuilder.scan(right).build();
        var missCondition = missBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.LESS_THAN,
                missBuilder.getRexBuilder().makeInputRef(missBarrier, 0),
                missBuilder.getRexBuilder().makeInputRef(missRight.getRowType().getFieldList().get(0).getType(), 2));
        RuleChecks.doesNotApply(rule, org.apache.calcite.rel.logical.LogicalJoin.create(missBarrier,
                missRight, java.util.List.of(), missCondition, java.util.Set.of(), JoinRelType.LEFT));
    }

    public static void main(String[] args) {
        System.out.println("Running PushLeakproofJoinIntoPermeableBarrierLeft test...");
        runTest();
    }
}

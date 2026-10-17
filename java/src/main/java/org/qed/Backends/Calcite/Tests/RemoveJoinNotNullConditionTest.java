package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.RemoveJoinNotNullCondition;
import org.qed.RuleBuilder;

public final class RemoveJoinNotNullConditionTest {
    private RemoveJoinNotNullConditionTest() {}

    public static void runTest() {
        var rule = new RemoveJoinNotNullCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RemoveJoinNotNullCondition rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, false);
        var rightName = RuleChecks.table(builder, 1, false, true);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var rex = builder.getRexBuilder();
        RexNode leftRef = rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        RexNode rightRef = rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        var rest = rex.makeCall(SqlStdOperatorTable.EQUALS, leftRef, rightRef);
        var notNull = rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, leftRef);
        RelNode plan = LogicalJoin.create(left, right, java.util.List.of(),
                rex.makeCall(SqlStdOperatorTable.AND, notNull, rest),
                java.util.Set.of(), JoinRelType.LEFT);

        RelBuilder plain = RuleChecks.ruleBuilder(builder);
        RelNode expectedLeft = plain.push(left).build();
        RelNode expectedRight = plain.push(right).build();
        var expectedRex = plain.getRexBuilder();
        RexNode expectedLeftRef = expectedRex.makeInputRef(expectedLeft.getRowType().getFieldList().get(0).getType(), 0);
        RexNode expectedRightRef = expectedRex.makeInputRef(expectedRight.getRowType().getFieldList().get(0).getType(), 1);
        RelNode expected = LogicalJoin.create(expectedLeft, expectedRight, java.util.List.of(),
                expectedRex.makeCall(SqlStdOperatorTable.EQUALS, expectedLeftRef, expectedRightRef),
                java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.rewrites(rule, plan, expected);

        var nearBuilder = RuleBuilder.create();
        var nullableLeftName = RuleChecks.table(nearBuilder, 1, false, true);
        var nullableRightName = RuleChecks.table(nearBuilder, 1, false, true);
        var nearLeft = nearBuilder.scan(nullableLeftName).build();
        var nearRight = nearBuilder.scan(nullableRightName).build();
        var nearRex = nearBuilder.getRexBuilder();
        RexNode nearLeftRef = nearRex.makeInputRef(nearLeft.getRowType().getFieldList().get(0).getType(), 0);
        RexNode nearRightRef = nearRex.makeInputRef(nearRight.getRowType().getFieldList().get(0).getType(), 1);
        RexNode nearRest = nearRex.makeCall(SqlStdOperatorTable.EQUALS, nearLeftRef, nearRightRef);
        RelNode nearMiss = LogicalJoin.create(nearLeft, nearRight, java.util.List.of(),
                nearRex.makeCall(SqlStdOperatorTable.AND,
                        nearRex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, nearLeftRef), nearRest),
                java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveJoinNotNullCondition test...");
        runTest();
    }
}

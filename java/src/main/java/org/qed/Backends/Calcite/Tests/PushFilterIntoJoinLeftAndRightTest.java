package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushFilterIntoJoinLeftAndRight;
import org.qed.RuleBuilder;

public class PushFilterIntoJoinLeftAndRightTest {
    public static void runTest() {
        var rule = new PushFilterIntoJoinLeftAndRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushFilterIntoJoinLeftAndRight rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 2, false, false);
        var rightName = RuleChecks.table(builder, 2, false, false);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var rex = left.getCluster().getRexBuilder();
        var leftType = left.getRowType();
        var rightType = right.getRowType();
        var l0 = rex.makeInputRef(leftType.getFieldList().get(0).getType(), 0);
        var l1 = rex.makeInputRef(leftType.getFieldList().get(1).getType(), 1);
        var r0 = rex.makeInputRef(rightType.getFieldList().get(0).getType(), 2);
        var r1 = rex.makeInputRef(rightType.getFieldList().get(1).getType(), 3);
        var condition = rex.makeCall(SqlStdOperatorTable.AND,
                rex.makeCall(SqlStdOperatorTable.EQUALS, l0, r0),
                rex.makeCall(SqlStdOperatorTable.EQUALS, l1, r1),
                rex.makeCall(SqlStdOperatorTable.EQUALS, l0, r1));
        var plan = LogicalJoin.create(left, right, java.util.List.of(), condition, java.util.Set.of(), JoinRelType.INNER);

        var plain = RuleChecks.ruleBuilder(builder);
        var expectedLeft = LogicalFilter.create(left, plain.call(SqlStdOperatorTable.EQUALS,
                plain.getRexBuilder().makeInputRef(leftType.getFieldList().get(0).getType(), 0),
                plain.getRexBuilder().makeInputRef(leftType.getFieldList().get(1).getType(), 1)));
        var expectedRight = LogicalFilter.create(right, plain.call(SqlStdOperatorTable.EQUALS,
                plain.getRexBuilder().makeInputRef(rightType.getFieldList().get(0).getType(), 0),
                plain.getRexBuilder().makeInputRef(rightType.getFieldList().get(1).getType(), 1)));
        var expectedCondition = plain.call(SqlStdOperatorTable.AND,
                plain.call(SqlStdOperatorTable.EQUALS,
                        plain.getRexBuilder().makeInputRef(leftType.getFieldList().get(0).getType(), 0),
                        plain.getRexBuilder().makeInputRef(rightType.getFieldList().get(0).getType(), 2)),
                plain.call(SqlStdOperatorTable.EQUALS,
                        plain.getRexBuilder().makeInputRef(leftType.getFieldList().get(1).getType(), 1),
                        plain.getRexBuilder().makeInputRef(rightType.getFieldList().get(1).getType(), 3)));
        var expected = LogicalJoin.create(expectedLeft, expectedRight, java.util.List.of(), expectedCondition,
                java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, plan, expected);

        var leftJoin = LogicalJoin.create(left, right, java.util.List.of(), condition, java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, leftJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running PushFilterIntoJoinLeftAndRight test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ExpandDisjunctionForJoinInputs;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

public final class ExpandDisjunctionForJoinInputsTest {
    public static void runTest() {
        var rule = new ExpandDisjunctionForJoinInputs();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltJoin();
        rejectsHandBuiltNearMiss();
    }

    private static void rewritesHandBuiltJoin() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 2, false);
        var rightName = RuleChecks.table(builder, 2, false);
        var b = RuleChecks.ruleBuilder(builder);
        RelNode left = b.scan(leftName).build();
        RelNode right = b.scan(rightName).build();
        var rex = b.getRexBuilder();
        RexNode l0 = rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        RexNode l1 = rex.makeInputRef(left.getRowType().getFieldList().get(1).getType(), 1);
        RexNode r0 = rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 2);
        RexNode r1 = rex.makeInputRef(right.getRowType().getFieldList().get(1).getType(), 3);
        RexNode p = RuleChecks.gt(b, l0, 1);
        RexNode q = RuleChecks.gt(b, l1, 2);
        RexNode u = RuleChecks.gt(b, r0, 3);
        RexNode v = RuleChecks.gt(b, r1, 4);
        RexNode original = rex.makeCall(SqlStdOperatorTable.OR,
                rex.makeCall(SqlStdOperatorTable.AND, p, u), rex.makeCall(SqlStdOperatorTable.AND, q, v));
        RelNode before = LogicalJoin.create(left, right, List.of(), original, Set.of(), JoinRelType.INNER);
        RexNode expectedCondition = rex.makeCall(SqlStdOperatorTable.AND, original,
                rex.makeCall(SqlStdOperatorTable.OR, p, q), rex.makeCall(SqlStdOperatorTable.OR, u, v));
        RelNode expected = LogicalJoin.create(left, right, List.of(), expectedCondition, Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(new ExpandDisjunctionForJoinInputs(), before, expected);
    }

    private static void rejectsHandBuiltNearMiss() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 2, false);
        var rightName = RuleChecks.table(builder, 2, false);
        var b = RuleChecks.ruleBuilder(builder);
        RelNode left = b.scan(leftName).build();
        RelNode right = b.scan(rightName).build();
        var rex = b.getRexBuilder();
        RexNode l0 = rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        RexNode l1 = rex.makeInputRef(left.getRowType().getFieldList().get(1).getType(), 1);
        RexNode r0 = rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 2);
        RexNode r1 = rex.makeInputRef(right.getRowType().getFieldList().get(1).getType(), 3);
        RexNode p = RuleChecks.gt(b, l0, 1);
        RexNode q = RuleChecks.gt(b, l1, 2);
        RexNode u = RuleChecks.gt(b, r0, 3);
        RexNode v = RuleChecks.gt(b, r1, 4);
        RexNode condition = rex.makeCall(SqlStdOperatorTable.OR,
                rex.makeCall(SqlStdOperatorTable.AND, p, q), rex.makeCall(SqlStdOperatorTable.AND, u, v));
        RelNode nearMiss = LogicalJoin.create(left, right, List.of(), condition, Set.of(), JoinRelType.INNER);
        RuleChecks.doesNotApply(new ExpandDisjunctionForJoinInputs(), nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running ExpandDisjunctionForJoinInputs test...");
        runTest();
    }
}

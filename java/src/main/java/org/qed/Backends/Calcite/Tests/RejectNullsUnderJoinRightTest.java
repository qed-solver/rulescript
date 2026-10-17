package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.RejectNullsUnderJoinRight;
import org.qed.RuleBuilder;

import java.util.List;

public final class RejectNullsUnderJoinRightTest {
    public static void runTest() {
        var rule = new RejectNullsUnderJoinRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RejectNullsUnderJoinRight rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var left = plain.scan(l).build();
        var right = plain.scan(r).build();
        var rexBuilder = plain.getRexBuilder();
        var rightRef = rexBuilder.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 0);
        var leftRef = rexBuilder.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var joinedRightRef = rexBuilder.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        var inputCondition = rexBuilder.makeCall(SqlStdOperatorTable.AND,
                rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, leftRef, joinedRightRef),
                rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_NULL, joinedRightRef));
        var plan = LogicalJoin.create(left, right, List.of(), inputCondition,
                java.util.Set.of(), JoinRelType.INNER);
        var filteredRight = LogicalFilter.create(right, rexBuilder.makeCall(
                SqlStdOperatorTable.IS_NOT_NULL, List.of(rightRef)));
        var expectedCondition = rexBuilder.makeCall(SqlStdOperatorTable.AND,
                rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, leftRef, joinedRightRef),
                rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_NULL, joinedRightRef));
        var expected = LogicalJoin.create(left, filteredRight, List.of(), expectedCondition,
                java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, plan, expected);

        var leftJoin = LogicalJoin.create(left, right, List.of(), inputCondition,
                java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, leftJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running RejectNullsUnderJoinRight test...");
        runTest();
    }
}

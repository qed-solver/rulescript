package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PushSelectIntoJoinLeft;
import org.qed.RuleBuilder;
import java.util.List;

public final class PushSelectIntoJoinLeftTest {
    public static void runTest() {
        var rule = new PushSelectIntoJoinLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoJoinLeft rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false, true);
        var right = RuleChecks.table(builder, 1, false, true);
        SqlOperator fOp = builder.genericPredicateOp("left_bound_filter", true);
        SqlOperator uOp = builder.genericPredicateOp("unbound_filter", true);
        SqlOperator gOp = builder.genericPredicateOp("join_on", true);
        var inputBuilder = builder.transform(config -> CalciteRuntime.configure(config).withSimplifyValues(false));
        var sourceLeft = inputBuilder.scan(left).build();
        var sourceRight = inputBuilder.scan(right).build();
        var inputRex = inputBuilder.getRexBuilder();
        RelNode sourceSeed = LogicalJoin.create(sourceLeft, sourceRight, List.of(), inputRex.makeLiteral(true),
                java.util.Set.of(), JoinRelType.LEFT);
        var beforeJoinCond = inputRex.makeCall(gOp, List.of(
                inputRex.makeInputRef(sourceLeft.getRowType().getFieldList().get(0).getType(), 0),
                inputRex.makeInputRef(sourceRight.getRowType().getFieldList().get(0).getType(), 1)));
        RelNode sourceJoin = ((LogicalJoin) sourceSeed).copy(sourceSeed.getTraitSet(), beforeJoinCond,
                sourceLeft, sourceRight, JoinRelType.LEFT, false);
        var sourceL = inputRex.makeInputRef(sourceJoin.getRowType().getFieldList().get(0).getType(), 0);
        var sourceR = inputRex.makeInputRef(sourceJoin.getRowType().getFieldList().get(1).getType(), 1);
        var beforeCondition = inputRex.makeCall(SqlStdOperatorTable.AND, List.of(
                inputRex.makeCall(fOp, List.of(sourceL)), inputRex.makeCall(uOp, List.of(sourceL, sourceR))));
        RelNode before = LogicalFilter.create(sourceJoin, beforeCondition);
        RelBuilder plain = RuleChecks.ruleBuilder(builder);
        var leftScan = plain.scan(left).build();
        var rightScan = plain.scan(right).build();
        var leftRef = plain.getRexBuilder().makeInputRef(leftScan, 0);
        RelNode filteredLeft = LogicalFilter.create(leftScan, plain.getRexBuilder().makeCall(fOp, List.of(leftRef)));
        var leftJoinRef = plain.getRexBuilder().makeInputRef(filteredLeft.getRowType().getFieldList().get(0).getType(), 0);
        var rightJoinRef = plain.getRexBuilder().makeInputRef(rightScan.getRowType().getFieldList().get(0).getType(), 0);
        RelNode joinSeed = LogicalJoin.create(filteredLeft, rightScan, List.of(), plain.getRexBuilder().makeLiteral(true),
                java.util.Set.of(), JoinRelType.LEFT);
        var joinCondition = plain.getRexBuilder().makeCall(gOp, List.of(leftJoinRef,
                plain.getRexBuilder().makeInputRef(rightJoinRef.getType(), 1)));
        RelNode joined = ((LogicalJoin) joinSeed).copy(joinSeed.getTraitSet(), joinCondition,
                filteredLeft, rightScan, JoinRelType.LEFT, false);
        var joinedLeftRef = plain.getRexBuilder().makeInputRef(joined.getRowType().getFieldList().get(0).getType(), 0);
        var joinedRightRef = plain.getRexBuilder().makeInputRef(joined.getRowType().getFieldList().get(1).getType(), 1);
        RelNode expected = LogicalFilter.create(joined, plain.getRexBuilder().makeCall(uOp, List.of(joinedLeftRef, joinedRightRef)));
        RuleChecks.rewrites(rule, before, expected);

        RelNode innerSeed = LogicalJoin.create(leftScan, rightScan, List.of(), inputBuilder.getRexBuilder().makeLiteral(true),
                java.util.Set.of(), JoinRelType.INNER);
        var innerJoinCond = inputBuilder.getRexBuilder().makeCall(gOp, List.of(
                inputBuilder.getRexBuilder().makeInputRef(leftScan.getRowType().getFieldList().get(0).getType(), 0),
                inputBuilder.getRexBuilder().makeInputRef(rightScan.getRowType().getFieldList().get(0).getType(), 1)));
        RelNode innerJoin = ((LogicalJoin) innerSeed).copy(innerSeed.getTraitSet(), innerJoinCond,
                leftScan, rightScan, JoinRelType.INNER, false);
        var innerL = inputBuilder.getRexBuilder().makeInputRef(innerJoin.getRowType().getFieldList().get(0).getType(), 0);
        var innerR = inputBuilder.getRexBuilder().makeInputRef(innerJoin.getRowType().getFieldList().get(1).getType(), 1);
        RelNode inner = LogicalFilter.create(innerJoin,
                inputBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.AND, List.of(
                        inputBuilder.getRexBuilder().makeCall(fOp, List.of(innerL)),
                        inputBuilder.getRexBuilder().makeCall(uOp, List.of(innerL, innerR)))));
        RuleChecks.doesNotApply(rule, inner);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoJoinLeft test...");
        runTest();
    }
}

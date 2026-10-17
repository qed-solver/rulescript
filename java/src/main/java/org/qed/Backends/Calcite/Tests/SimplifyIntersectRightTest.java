package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.SimplifyIntersectRight;
import org.qed.RuleBuilder;

import java.util.List;

public class SimplifyIntersectRightTest {
    public static void runTest() {
        var rule = new SimplifyIntersectRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, false);
        var rightName = RuleChecks.table(builder, 1, true, false);
        var rb = builder.transform(CalciteRuntime::configure);
        var left = rb.scan(leftName).build();
        var right = rb.scan(rightName).build();
        RelNode match = LogicalIntersect.create(List.of(left, right), false);
        var rex = match.getCluster().getRexBuilder();
        var rightRef = RexInputRef.of(0, right.getRowType());
        var leftRef = rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 1);
        var condition = rex.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, rightRef, leftRef);
        RelNode expectedJoin = LogicalJoin.create(right, left, List.of(), condition,
                java.util.Set.of(), JoinRelType.SEMI);
        var output = rex.makeCast(match.getRowType().getFieldList().get(0).getType(),
                RexInputRef.of(0, right.getRowType()));
        RelNode expected = org.apache.calcite.rel.logical.LogicalProject.create(expectedJoin, List.of(),
                List.of(output), List.of(match.getRowType().getFieldNames().get(0)));
        RuleChecks.rewrites(rule, match, expected);

        var nonUniqueBuilder = RuleBuilder.create();
        var nonUniqueLeft = RuleChecks.table(nonUniqueBuilder, 1, false, false);
        var nonUniqueRight = RuleChecks.table(nonUniqueBuilder, 1, false, false);
        var nonUniqueRb = nonUniqueBuilder.transform(CalciteRuntime::configure);
        RelNode nonUniqueMatch = LogicalIntersect.create(List.of(
                nonUniqueRb.scan(nonUniqueLeft).build(), nonUniqueRb.scan(nonUniqueRight).build()), false);
        RuleChecks.doesNotApply(rule, nonUniqueMatch);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyIntersectRight test...");
        runTest();
    }
}

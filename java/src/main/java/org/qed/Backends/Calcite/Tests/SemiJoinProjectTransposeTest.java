package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SemiJoinProjectTranspose;
import org.qed.RuleBuilder;

public final class SemiJoinProjectTransposeTest {
    private SemiJoinProjectTransposeTest() {}

    public static void runTest() {
        var rule = new SemiJoinProjectTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var q = RuleBuilder.create();
        var leftName = RuleChecks.table(q, 2, false);
        var rightName = RuleChecks.table(q, 2, false);
        var p0 = q.genericProjectionOp("hand_p0", org.qed.RelType.fromString("INTEGER", true));
        var p1 = q.genericProjectionOp("hand_p1", org.qed.RelType.fromString("INTEGER", true));
        var pred = q.genericPredicateOp("hand_pred", true);

        var sourceBuilder = builder(q);
        RelNode rawLeft = sourceBuilder.scan(leftName).build();
        RelNode right = builder(q).scan(rightName).build();
        RelNode leftProjected = builder(q).push(rawLeft).project(java.util.List.of(
                sourceBuilder.call(p0, sourceBuilder.getRexBuilder().makeInputRef(rawLeft, 0),
                        sourceBuilder.getRexBuilder().makeInputRef(rawLeft, 1)),
                sourceBuilder.call(p1, sourceBuilder.getRexBuilder().makeInputRef(rawLeft, 0),
                        sourceBuilder.getRexBuilder().makeInputRef(rawLeft, 1)))).build();
        var rb = builder(q);
        RexBuilder rex = rb.getRexBuilder();
        RexNode[] projectedRefs = refs(rex, leftProjected, right);
        RexNode condition = rex.makeCall(pred, projectedRefs);
        RelNode before = LogicalJoin.create(leftProjected, right, java.util.List.of(), condition,
                java.util.Set.of(), JoinRelType.SEMI);

        RexNode[] rawRefs = refs(rex, rawLeft, right);
        RexNode p0After = rex.makeCall(p0, rawRefs[0], rawRefs[1]);
        RexNode p1After = rex.makeCall(p1, rawRefs[0], rawRefs[1]);
        RexNode expectedCondition = rex.makeCall(pred, p0After, p1After, rawRefs[2], rawRefs[3]);
        RelNode joined = LogicalJoin.create(rawLeft, right, java.util.List.of(), expectedCondition,
                java.util.Set.of(), JoinRelType.SEMI);
        RelNode expected = builder(q).push(joined).project(java.util.List.of(
                rex.makeCall(p0, rex.makeInputRef(joined, 0), rex.makeInputRef(joined, 1)),
                rex.makeCall(p1, rex.makeInputRef(joined, 0), rex.makeInputRef(joined, 1)))).build();
        RuleChecks.rewrites(rule, before, expected);

        RelNode nearMiss = LogicalJoin.create(leftProjected, right, java.util.List.of(), condition,
                java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RelBuilder builder(RuleBuilder q) {
        return RuleChecks.ruleBuilder(q);
    }

    private static RexNode[] refs(RexBuilder rex, RelNode left, RelNode right) {
        return new RexNode[] { rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(left.getRowType().getFieldList().get(1).getType(), 1),
                rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 2),
                rex.makeInputRef(right.getRowType().getFieldList().get(1).getType(), 3) };
    }

    public static void main(String[] args) {
        System.out.println("Running SemiJoinProjectTranspose test...");
        runTest();
        if (CalciteTester.failures() > 0) System.exit(1);
    }
}

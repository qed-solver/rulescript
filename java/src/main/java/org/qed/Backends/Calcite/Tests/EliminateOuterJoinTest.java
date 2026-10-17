package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateOuterJoin;
import org.qed.RuleBuilder;

public final class EliminateOuterJoinTest {
    public static void runTest() {
        var rule = new EliminateOuterJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsHandBuiltNearMiss();
    }

    private static void rewritesHandBuiltPlan() {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var before = handBuilt(builder, left, right, JoinRelType.FULL, true, true);
        var expected = handBuilt(builder, left, right, JoinRelType.INNER, true, true);
        RuleChecks.rewrites(new EliminateOuterJoin(), before, expected);
    }

    private static void rejectsHandBuiltNearMiss() {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var plan = handBuilt(builder, left, right, JoinRelType.FULL, true, false);
        RuleChecks.doesNotApply(new EliminateOuterJoin(), plan);
    }

    private static org.apache.calcite.rel.RelNode handBuilt(RuleBuilder builder, String left, String right,
            JoinRelType joinType, boolean rejectLeft, boolean rejectRight) {
        builder.scan(left).scan(right);
        var leftType = builder.peek(1).getRowType().getFieldList().get(0).getType();
        var rightType = builder.peek().getRowType().getFieldList().get(0).getType();
        var on = RuleChecks.eq(builder, builder.getRexBuilder().makeInputRef(leftType, 0),
                builder.getRexBuilder().makeInputRef(rightType, 1));
        builder.join(joinType, on);
        var condition = RuleChecks.gt(builder, builder.field(0), 0);
        var conjuncts = new java.util.ArrayList<org.apache.calcite.rex.RexNode>();
        conjuncts.add(condition);
        if (rejectLeft) {
            conjuncts.add(builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(0)));
        }
        if (rejectRight) {
            conjuncts.add(builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(1)));
        }
        var predicate = builder.getRexBuilder().makeCall(SqlStdOperatorTable.AND, conjuncts);
        builder.push(LogicalFilter.create(builder.build(), predicate));
        return builder.build();
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateOuterJoin test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("EliminateOuterJoin test failed");
        }
    }
}

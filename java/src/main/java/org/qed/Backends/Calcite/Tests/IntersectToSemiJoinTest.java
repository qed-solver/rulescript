package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.IntersectToSemiJoin;
import org.qed.RuleBuilder;

public final class IntersectToSemiJoinTest {
    public static void runTest() {
        var rule = new IntersectToSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive();
        handBuiltNoMatch();
    }

    private static void handBuiltPositive() {
        var qed = RuleBuilder.create();
        var left = RuleChecks.table(qed, 1, false, true);
        var right = RuleChecks.table(qed, 1, false, true);
        var builder = RuleChecks.ruleBuilder(qed);
        var before = builder.scan(left).scan(right).intersect(false, 2).build();
        builder.scan(left).scan(right);
        var leftRel = builder.peek(1);
        var rightRel = builder.peek(0);
        var rexBuilder = builder.getRexBuilder();
        var condition = rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                rexBuilder.makeInputRef(leftRel.getRowType().getFieldList().get(0).getType(), 0),
                rexBuilder.makeInputRef(rightRel.getRowType().getFieldList().get(0).getType(), 1));
        var expected = builder.join(JoinRelType.SEMI, condition)
                .aggregate(builder.groupKey(builder.field(0))).build();
        RuleChecks.rewrites(new IntersectToSemiJoin(), before, expected);
    }

    private static void handBuiltNoMatch() {
        var qed = RuleBuilder.create();
        var left = RuleChecks.table(qed, 1, false, true);
        var right = RuleChecks.table(qed, 1, false, true);
        var builder = RuleChecks.ruleBuilder(qed);
        var allIntersect = builder.scan(left).scan(right).intersect(true, 2).build();
        RuleChecks.doesNotApply(new IntersectToSemiJoin(), allIntersect);
    }

    public static void main(String[] args) {
        System.out.println("Running IntersectToSemiJoin test...");
        runTest();
    }
}

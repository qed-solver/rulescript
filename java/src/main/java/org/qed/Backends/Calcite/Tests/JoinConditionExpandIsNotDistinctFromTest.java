package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.JoinConditionExpandIsNotDistinctFrom;
import org.qed.RelRN;
import org.qed.RexRN;

public final class JoinConditionExpandIsNotDistinctFromTest {
    public static void runTest() {
        var rule = new JoinConditionExpandIsNotDistinctFrom();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltNullableJoin();
        rejectsAdditionalJoinCondition();
    }

    private static void handBuiltNullableJoin() {
        var left = RelRN.scan("Left", "INTEGER");
        var right = RelRN.scan("Right", "INTEGER");
        var x = left.joinField(0, right);
        var y = left.joinField(1, right);
        var before = FaithfulPlans.of(left.join(JoinRelType.INNER,
                new RexRN.Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, Seq.of(x, y)), right));
        var equivalent = new RexRN.Or(Seq.of(
                new RexRN.And(Seq.of(
                        new RexRN.Pred(SqlStdOperatorTable.IS_NULL, Seq.of(x)),
                        new RexRN.Pred(SqlStdOperatorTable.IS_NULL, Seq.of(y)))),
                new RexRN.Pred(SqlStdOperatorTable.IS_TRUE, Seq.of(
                        new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(x, y))))));
        var expected = FaithfulPlans.expected(left.join(JoinRelType.INNER, equivalent, right));
        RuleChecks.rewrites(new JoinConditionExpandIsNotDistinctFrom(), before, expected);
    }

    private static void rejectsAdditionalJoinCondition() {
        var left = RelRN.scan("Left", "INTEGER");
        var right = RelRN.scan("Right", "INTEGER");
        var x = left.joinField(0, right);
        var y = left.joinField(1, right);
        var condition = new RexRN.And(Seq.of(
                new RexRN.Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, Seq.of(x, y)),
                new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL, Seq.of(x))));
        RelNode plan = FaithfulPlans.of(left.join(JoinRelType.INNER, condition, right));
        RuleChecks.doesNotApply(new JoinConditionExpandIsNotDistinctFrom(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running JoinConditionExpandIsNotDistinctFrom test...");
        runTest();
    }
}

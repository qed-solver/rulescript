package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PushSelectCondLeftIntoJoinLeftAndRight;
import org.qed.RuleBuilder;

public final class PushSelectCondLeftIntoJoinLeftAndRightTest {
    public static void runTest() {
        var rule = new PushSelectCondLeftIntoJoinLeftAndRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectCondLeftIntoJoinLeftAndRight rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, false);
        var rightName = RuleChecks.table(builder, 1, false, false);
        SqlOperator predicate = builder.genericPredicateOp("P", true);
        var join = builder.scan(leftName).scan(rightName).join(JoinRelType.LEFT,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        var before = LogicalFilter.create(join,
                builder.getRexBuilder().makeCall(predicate, builder.getRexBuilder().makeInputRef(
                        join.getRowType().getFieldList().get(0).getType(), 0)));

        RelBuilder plain = RuleChecks.ruleBuilder(builder);
        var expectedLeft = plain.scan(leftName).filter(plain.call(predicate, plain.field(0))).build();
        var expectedRight = plain.scan(rightName).filter(plain.call(predicate, plain.field(0))).build();
        var expected = plain.push(expectedLeft).push(expectedRight)
                .join(JoinRelType.LEFT, plain.call(SqlStdOperatorTable.EQUALS,
                        plain.field(2, 0, 0), plain.field(2, 1, 0))).build();
        RuleChecks.rewrites(rule, before, expected);

        var wrongJoin = builder.scan(leftName).scan(rightName).join(JoinRelType.RIGHT,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        var nearMiss = LogicalFilter.create(wrongJoin,
                builder.getRexBuilder().makeCall(predicate, builder.getRexBuilder().makeInputRef(
                        wrongJoin.getRowType().getFieldList().get(0).getType(), 0)));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectCondLeftIntoJoinLeftAndRight test...");
        try {
            runTest();
            System.out.println("0 test failures");
        } catch (Throwable failure) {
            CalciteTester.fail(failure.getMessage());
        }
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ExpandDisjunctionForTable;
import org.qed.RuleBuilder;

public final class ExpandDisjunctionForTableTest {
    public static void runTest() {
        var rule = new ExpandDisjunctionForTable();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch(rule);
        meaningfulNoMatch(rule);
    }

    private static void handBuiltMatch(ExpandDisjunctionForTable rule) {
        var builder = RuleBuilder.create();
        var leftTable = RuleChecks.table(builder, 1, false);
        var rightTable = RuleChecks.table(builder, 1, false);
        builder.scan(leftTable);
        builder.scan(rightTable);
        var fields = builder.joinFields().toArray(RexNode[]::new);
        var left = fields[0];
        var right = fields[1];
        var a = predicate(builder, "left_one", left);
        var c = predicate(builder, "left_two", left);
        var b = predicate(builder, "right_one", right);
        var d = predicate(builder, "right_two", right);
        var cross = predicate(builder, "cross_match", left, right);
        var first = builder.call(SqlStdOperatorTable.AND, a, b);
        var second = builder.call(SqlStdOperatorTable.AND, c, d);
        var disjunction = builder.call(SqlStdOperatorTable.OR, first, second);
        var condition = builder.call(SqlStdOperatorTable.AND, cross, disjunction);
        builder.join(JoinRelType.INNER, condition);
        RelNode before = builder.build();

        var expectedBuilder = RuleBuilder.create();
        var expectedLeftTable = RuleChecks.table(expectedBuilder, 1, false);
        var expectedRightTable = RuleChecks.table(expectedBuilder, 1, false);
        expectedBuilder.scan(expectedLeftTable);
        expectedBuilder.scan(expectedRightTable);
        var expectedFields = expectedBuilder.joinFields().toArray(RexNode[]::new);
        var expectedLeft = predicate(expectedBuilder, "left_one", expectedFields[0]);
        var expectedLeftTwo = predicate(expectedBuilder, "left_two", expectedFields[0]);
        var expectedRight = predicate(expectedBuilder, "right_one", expectedFields[1]);
        var expectedRightTwo = predicate(expectedBuilder, "right_two", expectedFields[1]);
        var expectedCross = predicate(expectedBuilder, "cross_match", expectedFields[0], expectedFields[1]);
        var expectedFirst = expectedBuilder.call(SqlStdOperatorTable.AND, expectedLeft, expectedRight);
        var expectedSecond = expectedBuilder.call(SqlStdOperatorTable.AND, expectedLeftTwo, expectedRightTwo);
        var expectedDisjunction = expectedBuilder.call(SqlStdOperatorTable.OR, expectedFirst, expectedSecond);
        var expectedCondition = expectedBuilder.call(SqlStdOperatorTable.AND, expectedCross, expectedDisjunction,
                expectedBuilder.call(SqlStdOperatorTable.OR, expectedLeft, expectedLeftTwo),
                expectedBuilder.call(SqlStdOperatorTable.OR, expectedRight, expectedRightTwo));
        expectedBuilder.join(JoinRelType.INNER, expectedCondition);
        RuleChecks.rewrites(rule, before, expectedBuilder.build());
    }

    private static RexNode predicate(RuleBuilder builder, String name, RexNode... args) {
        return builder.call(builder.genericPredicateOp(name, true), args);
    }

    private static void meaningfulNoMatch(ExpandDisjunctionForTable rule) {
        var builder = RuleBuilder.create();
        var leftTable = RuleChecks.table(builder, 1, false);
        var rightTable = RuleChecks.table(builder, 1, false);
        builder.scan(leftTable).scan(rightTable).join(JoinRelType.LEFT, builder.literal(true));
        RuleChecks.doesNotApply(rule, builder.build());
    }

    public static void main(String[] args) {
        System.out.println("Running ExpandDisjunctionForTable test...");
        runTest();
    }
}

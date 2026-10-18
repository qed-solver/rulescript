package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RuleBuilder;
import org.qed.RRule;

public final class PushDownPredicatesTest {
    public static void main(String[] args) {
        System.out.println("Running PushDownPredicates test...");
        runTest();
    }

    public static void runTest() {
        RRule rule = new org.qed.RRuleInstances.PushDownPredicates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false, false);
        var right = RuleChecks.table(builder, 1, false, false);
        var l = builder.scan(left).build();
        var r = builder.scan(right).build();
        var inputBuilder = RuleChecks.ruleBuilder(builder);
        inputBuilder.scan(left).scan(right);
        var joinCondition = inputBuilder.call(SqlStdOperatorTable.EQUALS,
                inputBuilder.field(2, 0, 0), inputBuilder.field(2, 1, 0));
        inputBuilder.join(JoinRelType.INNER, joinCondition);
        var predicate = inputBuilder.call(SqlStdOperatorTable.GREATER_THAN,
                inputBuilder.field(0), inputBuilder.literal(5));
        var plan = inputBuilder.filter(predicate).build();
        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        expectedBuilder.scan(left);
        var expectedPredicate = expectedBuilder.call(SqlStdOperatorTable.GREATER_THAN,
                expectedBuilder.field(0), expectedBuilder.literal(5));
        expectedBuilder.filter(expectedPredicate);
        expectedBuilder.scan(right);
        var expectedJoin = expectedBuilder.call(SqlStdOperatorTable.EQUALS,
                expectedBuilder.field(2, 0, 0), expectedBuilder.field(2, 1, 0));
        var expectedPlan = expectedBuilder.join(JoinRelType.INNER, expectedJoin).build();
        RuleChecks.rewrites(rule, plan, expectedPlan);

        var leftJoinBuilder = RuleChecks.ruleBuilder(builder);
        leftJoinBuilder.scan(left).scan(right);
        var leftJoinPredicate = leftJoinBuilder.call(SqlStdOperatorTable.GREATER_THAN,
                leftJoinBuilder.field(2, 0, 0), leftJoinBuilder.literal(5));
        var leftJoinCondition = leftJoinBuilder.call(SqlStdOperatorTable.EQUALS,
                leftJoinBuilder.field(2, 0, 0), leftJoinBuilder.field(2, 1, 0));
        var leftJoin = leftJoinBuilder.join(JoinRelType.LEFT, leftJoinCondition)
                .filter(leftJoinPredicate).build();
        RuleChecks.doesNotApply(rule, leftJoin);
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RuleBuilder;

public class RejectNullsUnderJoinLeftTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.RejectNullsUnderJoinLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.qed.RRuleInstances.RejectNullsUnderJoinLeft rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var left = builder.scan(l).build();
        var right = builder.scan(r).build();
        var rexBuilder = builder.getRexBuilder();
        var equality = rexBuilder.makeCall(SqlStdOperatorTable.EQUALS,
                rexBuilder.makeInputRef(left, 0), rexBuilder.makeInputRef(right, 0));
        var isNotNull = rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_NULL, rexBuilder.makeInputRef(left, 0));
        var condition = rexBuilder.makeCall(SqlStdOperatorTable.AND, equality, isNotNull);
        var plan = org.apache.calcite.rel.logical.LogicalJoin.create(left, right,
                java.util.List.of(), condition, java.util.Set.of(), JoinRelType.INNER);

        var plain = RuleChecks.ruleBuilder(builder);
        var expectedLeft = plain.scan(l).build();
        var expectedRight = plain.scan(r).build();
        var expectedFilter = plain.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                plain.getRexBuilder().makeInputRef(expectedLeft, 0));
        var filteredLeft = org.apache.calcite.rel.logical.LogicalFilter.create(expectedLeft, expectedFilter);
        var expectedEquality = plain.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                plain.getRexBuilder().makeInputRef(filteredLeft, 0), plain.getRexBuilder().makeInputRef(expectedRight, 0));
        var expectedIsNotNull = plain.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                plain.getRexBuilder().makeInputRef(filteredLeft, 0));
        var expectedCondition = plain.getRexBuilder().makeCall(SqlStdOperatorTable.AND, expectedEquality, expectedIsNotNull);
        var expected = org.apache.calcite.rel.logical.LogicalJoin.create(filteredLeft, expectedRight,
                java.util.List.of(), expectedCondition, java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, plan, expected);

        var nonNullBuilder = RuleBuilder.create();
        var nonNullLeft = RuleChecks.table(nonNullBuilder, 1, false, false);
        var nullableRight = RuleChecks.table(nonNullBuilder, 1, false);
        var nonNullNearMiss = nonNullBuilder.scan(nonNullLeft).scan(nullableRight)
                .join(JoinRelType.INNER,
                        nonNullBuilder.and(
                                nonNullBuilder.call(SqlStdOperatorTable.EQUALS,
                                        nonNullBuilder.field(2, 0, 0), nonNullBuilder.field(2, 1, 0)),
                                nonNullBuilder.call(SqlStdOperatorTable.IS_NOT_NULL,
                                        nonNullBuilder.field(2, 0, 0))))
                .build();
        RuleChecks.doesNotApply(rule, nonNullNearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RejectNullsUnderJoinLeft test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError(CalciteTester.failures() + " test failures");
        }
    }
}

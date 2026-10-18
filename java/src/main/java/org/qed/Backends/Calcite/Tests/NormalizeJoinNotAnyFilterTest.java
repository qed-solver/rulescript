package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RRuleInstances.NormalizeJoinNotAnyFilter;
import org.qed.RuleBuilder;

import java.util.List;

public class NormalizeJoinNotAnyFilterTest {
    public static void runTest() {
        var rule = new NormalizeJoinNotAnyFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, true);
        var rightName = RuleChecks.table(builder, 1, false, true);
        var subName = RuleChecks.table(builder, 1, false, true);
        RelNode left = builder.scan(leftName).build();
        RelNode right = builder.scan(rightName).build();
        RelNode sub = builder.scan(subName).build();
        var rexBuilder = builder.getRexBuilder();
        RexNode notAny = rexBuilder.makeCall(SqlStdOperatorTable.NOT,
                builder.in(sub, List.of(rexBuilder.makeInputRef(left, 0))));
        RelNode before = LogicalJoin.create(left, right, List.of(), notAny,
                com.google.common.collect.ImmutableSet.of(), JoinRelType.INNER);

        var corrId = builder.getCluster().createCorrel();
        var corr = rexBuilder.makeCorrel(before.getRowType(), corrId);
        RexNode equals = rexBuilder.makeCall(SqlStdOperatorTable.EQUALS,
                rexBuilder.makeFieldAccess(corr, 0),
                rexBuilder.makeInputRef(sub.getRowType().getFieldList().get(0).getType(), 0));
        RexNode matchesOrNull = rexBuilder.makeCall(SqlStdOperatorTable.OR, equals,
                rexBuilder.makeCall(SqlStdOperatorTable.IS_NULL, equals));
        RexNode exists = RexSubQuery.exists(
                org.apache.calcite.rel.logical.LogicalFilter.create(sub, matchesOrNull));
        RelNode after = LogicalJoin.create(left, right, List.of(),
                rexBuilder.makeCall(SqlStdOperatorTable.NOT, exists),
                com.google.common.collect.ImmutableSet.of(corrId), JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, after);

        RelNode leftJoinNearMiss = LogicalJoin.create(left, right, List.of(), notAny,
                com.google.common.collect.ImmutableSet.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, leftJoinNearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}

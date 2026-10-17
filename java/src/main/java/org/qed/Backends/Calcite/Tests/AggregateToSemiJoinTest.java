package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateToSemiJoin;

public final class AggregateToSemiJoinTest {
    public static void runTest() {
        var rule = new AggregateToSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false);
        var rightName = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var rightAggregate = b.push(right).aggregate(b.groupKey(b.field(0))).build();
        var leftRef = b.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var rightRef = b.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        var input = b.push(left).push(rightAggregate)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.EQUALS, leftRef, rightRef))
                .aggregate(b.groupKey(b.field(0)))
                .build();
        var expected = b.push(left).push(right)
                .join(JoinRelType.SEMI, b.call(SqlStdOperatorTable.EQUALS, leftRef, rightRef))
                .aggregate(b.groupKey(b.field(0)))
                .build();
        RuleChecks.rewrites(rule, input, expected);

        var nearMiss = b.push(left).push(right)
                .join(JoinRelType.LEFT, b.call(SqlStdOperatorTable.EQUALS, rightRef, leftRef))
                .aggregate(b.groupKey(b.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateToSemiJoin test...");
        runTest();
    }
}

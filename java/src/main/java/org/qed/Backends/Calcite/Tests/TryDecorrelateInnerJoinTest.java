package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.TryDecorrelateInnerJoin;
import org.qed.RuleBuilder;

public class TryDecorrelateInnerJoinTest {

    public static void runTest() {
        var rule = new TryDecorrelateInnerJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateInnerJoin rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var innerLeft = RuleChecks.table(builder, 1, false);
        var innerRight = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);

        var innerJoin = b.scan(innerLeft).scan(innerRight)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        var before = b.scan(left).push(innerJoin)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 1)))
                .build();
        var after = b.scan(left).scan(innerLeft).scan(innerRight)
                .join(JoinRelType.INNER, b.literal(true))
                .join(JoinRelType.INNER, b.and(
                        RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 1)),
                        b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 1, 0), b.field(2, 1, 1))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var leftInnerJoin = b.scan(innerLeft).scan(innerRight)
                .join(JoinRelType.LEFT, b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        var nearMiss = b.scan(left).push(leftInnerJoin)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 1)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateInnerJoin test...");
        runTest();
    }
}

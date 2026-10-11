package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.RejectNullsUnderJoinRight;
import org.qed.RuleBuilder;

public class RejectNullsUnderJoinRightTest {

    public static void runTest() {
        var rule = new RejectNullsUnderJoinRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RejectNullsUnderJoinRight rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var on = builder.genericPredicateOp("join_on", true);
        var before = builder.scan(left).scan(right)
                .join(JoinRelType.INNER, builder.and(
                        builder.call(on, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(2, 1, 0))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(left).scan(right)
                .filter(plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(0)))
                .join(JoinRelType.INNER, plain.and(
                        plain.call(on, plain.field(2, 0, 0), plain.field(2, 1, 0)),
                        plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(2, 1, 0))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, builder.scan(left).scan(right)
                .join(JoinRelType.LEFT, builder.and(
                        builder.call(on, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(2, 1, 0))))
                .build());
        RuleChecks.doesNotApply(rule, builder.scan(left).scan(right)
                .join(JoinRelType.INNER, builder.and(
                        builder.call(on, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(2, 0, 0))))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running RejectNullsUnderJoinRight test...");
        runTest();
    }
}

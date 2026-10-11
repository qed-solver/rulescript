package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.RejectNullsUnderJoinLeft;
import org.qed.RuleBuilder;

public class RejectNullsUnderJoinLeftTest {

    public static void runTest() {
        var rule = new RejectNullsUnderJoinLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RejectNullsUnderJoinLeft rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var on = builder.call(SqlStdOperatorTable.EQUALS, builder.scan(l).scan(r).field(2, 0, 0),
                builder.field(2, 1, 0));
        var notNull = builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(2, 0, 0));
        var before = builder.join(JoinRelType.INNER, builder.and(on, notNull)).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).filter(plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(0)))
                .scan(r)
                .join(JoinRelType.INNER, plain.and(
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)),
                        plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(2, 0, 0))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var rightOnly = builder.scan(l).scan(r)
                .join(JoinRelType.INNER, builder.and(
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(2, 1, 0))))
                .build();
        RuleChecks.doesNotApply(rule, rightOnly);
    }

    public static void main(String[] args) {
        System.out.println("Running RejectNullsUnderJoinLeft test...");
        runTest();
    }
}

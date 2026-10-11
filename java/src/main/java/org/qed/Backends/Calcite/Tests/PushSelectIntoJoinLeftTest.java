package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushSelectIntoJoinLeft;
import org.qed.RuleBuilder;

public class PushSelectIntoJoinLeftTest {

    public static void runTest() {
        var rule = new PushSelectIntoJoinLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoJoinLeft rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var before = builder.scan(left).scan(right)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(left)
                .filter(RuleChecks.gt(plain, plain.field(0), 5))
                .scan(right)
                .join(JoinRelType.LEFT, RuleChecks.eq(plain, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .filter(RuleChecks.eq(plain, plain.field(0), plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var inner = builder.scan(left).scan(right)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1))))
                .build();
        RuleChecks.doesNotApply(rule, inner);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoJoinLeft test...");
        runTest();
    }
}

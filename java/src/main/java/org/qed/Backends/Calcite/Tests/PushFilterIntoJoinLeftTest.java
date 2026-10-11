package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushFilterIntoJoinLeft;
import org.qed.RuleBuilder;

public class PushFilterIntoJoinLeftTest {

    public static void runTest() {
        var rule = new PushFilterIntoJoinLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushFilterIntoJoinLeft rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var before = builder.scan(left).scan(right)
                .join(JoinRelType.INNER, builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 0, 0), five),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(left)
                .filter(RuleChecks.gt(plain, plain.field(0), 5))
                .scan(right)
                .join(JoinRelType.INNER, RuleChecks.eq(plain, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var outer = builder.scan(left).scan(right)
                .join(JoinRelType.LEFT, builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 0, 0), five),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))))
                .build();
        RuleChecks.doesNotApply(rule, outer);
    }

    public static void main(String[] args) {
        System.out.println("Running PushFilterIntoJoinLeft test...");
        runTest();
    }
}

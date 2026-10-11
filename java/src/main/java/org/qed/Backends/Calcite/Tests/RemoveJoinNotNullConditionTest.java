package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.RemoveJoinNotNullCondition;
import org.qed.RuleBuilder;

public class RemoveJoinNotNullConditionTest {

    public static void runTest() {
        var rule = new RemoveJoinNotNullCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RemoveJoinNotNullCondition rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false, false);
        var right = RuleChecks.table(builder, 1, false);
        var before = builder.scan(left).scan(right)
                .join(JoinRelType.LEFT, builder.and(
                        builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(2, 0, 0)),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(left).scan(right)
                .join(JoinRelType.LEFT, RuleChecks.eq(plain, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var inner = builder.scan(left).scan(right)
                .join(JoinRelType.INNER, builder.and(
                        builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(2, 0, 0)),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))))
                .build();
        RuleChecks.doesNotApply(rule, inner);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveJoinNotNullCondition test...");
        runTest();
    }
}

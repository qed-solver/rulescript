package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PushPredicateThroughJoin;
import org.qed.RuleBuilder;

public class PushPredicateThroughJoinTest {
    public static void runTest() {
        var rule = new PushPredicateThroughJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushPredicateThroughJoin rule) {
        var builder = RuleBuilder.create();
        var leftTable = RuleChecks.table(builder, 1, false, false);
        var rightTable = RuleChecks.table(builder, 1, false, false);
        builder.scan(leftTable).scan(rightTable);
        var joinCondition = builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0));
        builder.join(JoinRelType.INNER, joinCondition);
        var plan = builder
                .filter(builder.call(SqlStdOperatorTable.AND,
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(1, 0, 0), builder.literal(0)),
                        builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(1, 0, 1), builder.literal(10)),
                        builder.call(SqlStdOperatorTable.NOT_EQUALS, builder.field(1, 0, 0), builder.field(1, 0, 1))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(leftTable).filter(RuleChecks.gt(plain, plain.field(1, 0, 0), 0))
                .scan(rightTable).filter(plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(1, 0, 0), plain.literal(10)))
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.AND,
                        RuleChecks.eq(plain, plain.field(2, 0, 0), plain.field(2, 1, 0)),
                        plain.call(SqlStdOperatorTable.NOT_EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0))))
                .build();
        RuleChecks.rewrites(rule, plan, expected);

        var leftOuter = builder.scan(leftTable).scan(rightTable)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.call(SqlStdOperatorTable.AND,
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(1, 0, 0), builder.literal(0)),
                        builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(1, 0, 1), builder.literal(10)),
                        builder.call(SqlStdOperatorTable.NOT_EQUALS, builder.field(1, 0, 0), builder.field(1, 0, 1))))
                .build();
        RuleChecks.doesNotApply(rule, leftOuter);
    }

    public static void main(String[] args) {
        System.out.println("Running PushPredicateThroughJoin test...");
        runTest();
    }
}

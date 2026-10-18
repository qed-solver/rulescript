package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.RejectNullsRightJoin;
import org.qed.RuleBuilder;

public class RejectNullsRightJoinTest {
    public static void main(String[] args) {
        System.out.println("Running RejectNullsRightJoin test...");
        runTest();
    }

    public static void runTest() {
        var rule = new RejectNullsRightJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RejectNullsRightJoin rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false, false);
        var r = RuleChecks.table(builder, 1, false, false);
        var leftScan = builder.scan(l).build();
        var rightScan = builder.scan(r).build();
        var plan = builder.push(leftScan).push(rightScan).join(JoinRelType.FULL,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(1, 0, 0))).build();

        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.push(plain.scan(l).build()).push(plain.scan(r).build())
                .join(JoinRelType.LEFT,
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .filter(plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(1, 0, 0))).build();
        RuleChecks.rewrites(rule, plan, expected);

        var inner = builder.push(leftScan).push(rightScan).join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.field(1, 0, 0))).build();
        RuleChecks.doesNotApply(rule, inner);
    }
}

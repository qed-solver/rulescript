package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RuleBuilder;

public class PushDownJoinThroughUnionTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.PushDownJoinThroughUnion();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.qed.RRule rule) {
        var builder = RuleBuilder.create();
        var x = RuleChecks.table(builder, 1, false);
        var y = RuleChecks.table(builder, 1, false);
        var o = RuleChecks.table(builder, 1, false);
        var plan = builder.scan(x).scan(y).union(true, 2).scan(o)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.EQUALS,
                        builder.field(2, 0, 0), builder.field(2, 1, 0))).build();

        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(x).scan(o)
                .join(JoinRelType.LEFT, plain.call(SqlStdOperatorTable.EQUALS,
                        plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .scan(y).scan(o)
                .join(JoinRelType.LEFT, plain.call(SqlStdOperatorTable.EQUALS,
                        plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .union(true, 2).build();
        RuleChecks.rewrites(rule, plan, expected);

        var nearMiss = builder.scan(x).scan(y).union(true, 2).scan(o)
                .join(JoinRelType.RIGHT, builder.call(SqlStdOperatorTable.EQUALS,
                        builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushDownJoinThroughUnion test...");
        runTest();
    }
}

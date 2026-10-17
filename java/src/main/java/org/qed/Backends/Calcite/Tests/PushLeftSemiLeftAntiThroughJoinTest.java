package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RuleBuilder;

public class PushLeftSemiLeftAntiThroughJoinTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.PushLeftSemiLeftAntiThroughJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.qed.RRuleInstances.PushLeftSemiLeftAntiThroughJoin rule) {
        var builder = RuleBuilder.create();
        var x = RuleChecks.table(builder, 1, false, false);
        var y = RuleChecks.table(builder, 1, false, false);
        var z = RuleChecks.table(builder, 1, false, false);
        var before = builder.scan(x).scan(y).join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .scan(z).join(JoinRelType.SEMI,
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(x).scan(z).join(JoinRelType.SEMI,
                plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .scan(y).join(JoinRelType.INNER,
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, expected);

        var nearMiss = builder.scan(x).scan(y).join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .scan(z).join(JoinRelType.ANTI,
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushLeftSemiLeftAntiThroughJoin test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RuleBuilder;

public class PushPredicateThroughNonJoinTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.PushPredicateThroughNonJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.qed.RRuleInstances.PushPredicateThroughNonJoin rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var plan = builder.scan(table)
                .project(builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.literal(1)), builder.field(0))
                .filter(builder.and(
                        RuleChecks.gt(builder, builder.field(0), 10),
                        RuleChecks.gt(builder, builder.field(1), 20)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(table)
                .filter(RuleChecks.gt(plain, plain.call(SqlStdOperatorTable.PLUS, plain.field(0), plain.literal(1)), 10))
                .project(plain.call(SqlStdOperatorTable.PLUS, plain.field(0), plain.literal(1)), plain.field(0))
                .filter(RuleChecks.gt(plain, plain.field(1), 20))
                .build();
        RuleChecks.rewrites(rule, plan, expected);

        var nearMissBuilder = RuleBuilder.create();
        var nearMissTable = RuleChecks.table(nearMissBuilder, 1, false, false);
        var nearMiss = nearMissBuilder.scan(nearMissTable)
                .project(nearMissBuilder.call(SqlStdOperatorTable.PLUS, nearMissBuilder.field(0), nearMissBuilder.literal(1)), nearMissBuilder.field(0))
                .filter(nearMissBuilder.and(
                        RuleChecks.gt(nearMissBuilder, nearMissBuilder.field(0), 10),
                        RuleChecks.gt(nearMissBuilder, nearMissBuilder.field(0), 20)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushPredicateThroughNonJoin test...");
        try {
            runTest();
        } catch (RuntimeException e) {
            CalciteTester.fail(e.getMessage());
        }
    }
}

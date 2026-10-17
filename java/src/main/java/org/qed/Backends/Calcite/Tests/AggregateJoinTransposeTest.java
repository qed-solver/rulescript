package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateJoinTranspose;
import org.qed.RuleBuilder;

public class AggregateJoinTransposeTest {
    public static void runTest() {
        var rule = new AggregateJoinTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 2, false, false);
        var right = RuleChecks.table(builder, 2, false, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        var leftScan = relBuilder.scan(left).build();
        var rightScan = relBuilder.scan(right).build();
        var before = before(relBuilder, leftScan, rightScan, JoinRelType.INNER);
        var expected = after(relBuilder, leftScan, rightScan);
        RuleChecks.rewrites(rule, before, expected);
        var nearMiss = before(relBuilder, leftScan, rightScan, JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static org.apache.calcite.rel.RelNode before(RelBuilder builder,
            org.apache.calcite.rel.RelNode left, org.apache.calcite.rel.RelNode right, JoinRelType type) {
        return builder.push(left).push(right)
                .join(type, builder.call(SqlStdOperatorTable.LESS_THAN,
                        builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .aggregate(builder.groupKey(1, 3)).build();
    }

    private static org.apache.calcite.rel.RelNode after(RelBuilder builder,
            org.apache.calcite.rel.RelNode left, org.apache.calcite.rel.RelNode right) {
        return builder.push(left).aggregate(builder.groupKey(0, 1))
                .push(right).aggregate(builder.groupKey(0, 1))
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.LESS_THAN,
                        builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .aggregate(builder.groupKey(1, 3)).build();
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateJoinTranspose test...");
        runTest();
        System.out.println(CalciteTester.failures() + " test failures");
    }
}

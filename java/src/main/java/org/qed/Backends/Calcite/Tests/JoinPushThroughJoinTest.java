package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.JoinPushThroughJoin;
import org.qed.RuleBuilder;

public class JoinPushThroughJoinTest {
    public static void runTest() {
        var rule = new JoinPushThroughJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
        handBuiltRewrite();
        handBuiltNearMiss();
    }

    private static void ordinaryTables(org.qed.RRule rule) {
        var qedBuilder = RuleBuilder.create();
        var a = RuleChecks.table(qedBuilder, 1, false, false);
        var b = RuleChecks.table(qedBuilder, 1, false, false);
        var c = RuleChecks.table(qedBuilder, 1, false, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        var before = builder.scan(a).scan(b)
                .join(JoinRelType.INNER, builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 0, 0), builder.literal(0)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 1, 0), builder.literal(0))))
                .scan(c).join(JoinRelType.INNER, builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 0, 0), builder.field(2, 0, 1)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 1, 0), builder.literal(0))))
                .build();
        RuleChecks.rewrites(rule, before, expectedOrdinary(builder, a, b, c));
    }

    private static org.apache.calcite.rel.RelNode expectedOrdinary(org.apache.calcite.tools.RelBuilder builder, String a, String b, String c) {
        return builder.scan(a).scan(c)
                .join(JoinRelType.INNER, builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 0, 0), builder.literal(0)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 1, 0), builder.literal(0))))
                .scan(b).join(JoinRelType.INNER, builder.and(
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 1, 0), builder.literal(0))))
                .project(builder.field(1, 0, 0), builder.field(1, 0, 2), builder.field(1, 0, 1)).build();
    }

    private static void handBuiltRewrite() {
        var qb = RuleBuilder.create();
        var x = RuleChecks.table(qb, 1, false, false);
        var y = RuleChecks.table(qb, 1, false, false);
        var z = RuleChecks.table(qb, 1, false, false);
        var b = RuleChecks.ruleBuilder(qb);
        var left = b.scan(x).build();
        var middle = b.scan(y).build();
        var right = b.scan(z).build();
        var before = b.push(left).push(middle)
                .join(JoinRelType.INNER, b.and(RuleChecks.gt(b, b.field(2, 0, 0), 0),
                        RuleChecks.gt(b, b.field(2, 1, 0), 0)))
                .push(right).join(JoinRelType.INNER, b.and(
                        b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 0, 1)),
                        RuleChecks.gt(b, b.field(2, 1, 0), 0)))
                .build();
        var after = b.scan(x).scan(z)
                .join(JoinRelType.INNER, b.and(RuleChecks.gt(b, b.field(2, 0, 0), 0),
                        RuleChecks.gt(b, b.field(2, 1, 0), 0)))
                .scan(y).join(JoinRelType.INNER, b.and(
                        b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)),
                        RuleChecks.gt(b, b.field(2, 1, 0), 0)))
                .project(b.field(1, 0, 0), b.field(1, 0, 2), b.field(1, 0, 1))
                .build();
        RuleChecks.rewrites(new JoinPushThroughJoin(), before, after);
    }

    private static void handBuiltNearMiss() {
        var qb = RuleBuilder.create();
        var x = RuleChecks.table(qb, 1, false, false);
        var y = RuleChecks.table(qb, 1, false, false);
        var z = RuleChecks.table(qb, 1, false, false);
        var b = RuleChecks.ruleBuilder(qb);
        var left = b.scan(x).build();
        var middle = b.scan(y).build();
        var right = b.scan(z).build();
        var plan = b.push(left).push(middle)
                .join(JoinRelType.LEFT, b.and(RuleChecks.gt(b, b.field(2, 0, 0), 0),
                        RuleChecks.gt(b, b.field(2, 1, 0), 0)))
                .push(right).join(JoinRelType.INNER, b.and(
                        b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 0, 1)),
                        RuleChecks.gt(b, b.field(2, 1, 0), 0)))
                .build();
        RuleChecks.doesNotApply(new JoinPushThroughJoin(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}

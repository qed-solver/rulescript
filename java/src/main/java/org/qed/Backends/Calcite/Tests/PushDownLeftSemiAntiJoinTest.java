package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRuleInstances.PushDownLeftSemiAntiJoin;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PushDownLeftSemiAntiJoinTest {
    public static void runTest() {
        var rule = new PushDownLeftSemiAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushDownLeftSemiAntiJoin rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 2, false);
        var right = RuleChecks.table(builder, 2, false);
        SqlOperator p0 = builder.genericProjectionOp("P0", RelType.fromString("INTEGER", true));
        SqlOperator p1 = builder.genericProjectionOp("P1", RelType.fromString("INTEGER", true));
        SqlOperator c = builder.genericPredicateOp("C", true);
        var leftPlan = builder.scan(left).project(
                builder.call(p0, org.qed.Backends.Calcite.CalciteRuntime.field(builder, 1, 0), org.qed.Backends.Calcite.CalciteRuntime.field(builder, 1, 1)),
                builder.call(p1, org.qed.Backends.Calcite.CalciteRuntime.field(builder, 1, 0), org.qed.Backends.Calcite.CalciteRuntime.field(builder, 1, 1))).build();
        var before = builder.push(leftPlan).scan(right).join(JoinRelType.SEMI,
                builder.call(c, org.qed.Backends.Calcite.CalciteRuntime.field(builder, 2, 0), org.qed.Backends.Calcite.CalciteRuntime.field(builder, 2, 1), org.qed.Backends.Calcite.CalciteRuntime.field(builder, 2, 2), org.qed.Backends.Calcite.CalciteRuntime.field(builder, 2, 3))).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(left).scan(right).join(JoinRelType.SEMI,
                plain.call(c,
                        plain.call(p0, org.qed.Backends.Calcite.CalciteRuntime.field(plain, 2, 0), org.qed.Backends.Calcite.CalciteRuntime.field(plain, 2, 1)),
                        plain.call(p1, org.qed.Backends.Calcite.CalciteRuntime.field(plain, 2, 0), org.qed.Backends.Calcite.CalciteRuntime.field(plain, 2, 1)),
                        org.qed.Backends.Calcite.CalciteRuntime.field(plain, 2, 2), org.qed.Backends.Calcite.CalciteRuntime.field(plain, 2, 3)))
                .project(
                        plain.call(p0, org.qed.Backends.Calcite.CalciteRuntime.field(plain, 1, 0), org.qed.Backends.Calcite.CalciteRuntime.field(plain, 1, 1)),
                        plain.call(p1, org.qed.Backends.Calcite.CalciteRuntime.field(plain, 1, 0), org.qed.Backends.Calcite.CalciteRuntime.field(plain, 1, 1))).build();
        RuleChecks.rewrites(rule, before, expected);
        var nearMiss = builder.push(leftPlan).scan(right).join(JoinRelType.LEFT,
                builder.call(c, org.qed.Backends.Calcite.CalciteRuntime.field(builder, 2, 0), org.qed.Backends.Calcite.CalciteRuntime.field(builder, 2, 1), org.qed.Backends.Calcite.CalciteRuntime.field(builder, 2, 2), org.qed.Backends.Calcite.CalciteRuntime.field(builder, 2, 3))).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushDownLeftSemiAntiJoin test...");
        runTest();
    }
}

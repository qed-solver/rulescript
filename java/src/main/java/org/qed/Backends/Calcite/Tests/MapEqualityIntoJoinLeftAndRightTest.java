package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.MapEqualityIntoJoinLeftAndRight;
import org.qed.RuleBuilder;

public final class MapEqualityIntoJoinLeftAndRightTest {
    public static void runTest() {
        var rule = new MapEqualityIntoJoinLeftAndRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MapEqualityIntoJoinLeftAndRight rule) {
        var qed = RuleBuilder.create();
        var left = RuleChecks.table(qed, 2, false, false);
        var right = RuleChecks.table(qed, 1, false, false);
        var builder = qed;
        var before = builder.scan(left).scan(right).join(JoinRelType.INNER, builder.and(
                RuleChecks.eq(builder, builder.joinFields().get(0), builder.joinFields().get(2)),
                RuleChecks.eq(builder, builder.joinFields().get(2), builder.joinFields().get(1)))).build();

        qed = RuleBuilder.create();
        left = RuleChecks.table(qed, 2, false, false);
        right = RuleChecks.table(qed, 1, false, false);
        builder = qed;
        var expected = builder.scan(left).scan(right).join(JoinRelType.INNER, builder.and(
                RuleChecks.eq(builder, builder.joinFields().get(2), builder.joinFields().get(1)),
                RuleChecks.eq(builder, builder.joinFields().get(0), builder.joinFields().get(1)))).build();
        RuleChecks.rewrites(rule, before, expected);

        qed = RuleBuilder.create();
        left = RuleChecks.table(qed, 2, false, false);
        right = RuleChecks.table(qed, 1, false, false);
        builder = qed;
        var nearMiss = builder.scan(left).scan(right).join(JoinRelType.LEFT, builder.and(
                RuleChecks.eq(builder, builder.joinFields().get(0), builder.joinFields().get(2)),
                RuleChecks.eq(builder, builder.joinFields().get(2), builder.joinFields().get(1)))).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running MapEqualityIntoJoinLeftAndRight test...");
        runTest();
    }
}

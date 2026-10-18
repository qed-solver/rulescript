package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.RelNode;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PruneSemiAntiJoinRightCols;
import org.qed.RuleBuilder;

public class PruneSemiAntiJoinRightColsTest {
    public static void runTest() {
        var rule = new PruneSemiAntiJoinRightCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneSemiAntiJoinRightCols rule) {
        var builder = RuleBuilder.create();
        var leftTable = RuleChecks.table(builder, 1, false);
        var rightTable = RuleChecks.table(builder, 2, false);
        var left = builder.scan(leftTable).build();
        var right = builder.scan(rightTable).build();
        builder.push(left);
        builder.push(right);
        var beforeCondition = RuleChecks.eq(builder, CalciteRuntime.field(builder, 2, 0),
                CalciteRuntime.field(builder, 2, 1));
        RelNode before = org.apache.calcite.rel.logical.LogicalJoin.create(left, right, java.util.List.of(),
                beforeCondition, java.util.Set.of(), JoinRelType.ANTI);

        var expectedBuilder = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedBuilder, 1, false);
        var expectedRight = RuleChecks.table(expectedBuilder, 2, false);
        var expectedRightScan = expectedBuilder.scan(expectedRight).build();
        expectedBuilder.push(expectedRightScan);
        expectedBuilder.project(java.util.List.of(expectedBuilder.field(0)), java.util.List.of());
        var projectedRight = expectedBuilder.build();
        var expectedLeftScan = expectedBuilder.scan(expectedLeft).build();
        expectedBuilder.push(expectedLeftScan);
        expectedBuilder.push(projectedRight);
        var expectedCondition = RuleChecks.eq(expectedBuilder, expectedBuilder.field(2, 0, 0),
                expectedBuilder.field(2, 1, 0));
        RelNode after = org.apache.calcite.rel.logical.LogicalJoin.create(expectedLeftScan, projectedRight,
                java.util.List.of(), expectedCondition, java.util.Set.of(), JoinRelType.ANTI);
        RuleChecks.rewrites(rule, before, after);

        var nearMissBuilder = RuleBuilder.create();
        var nearLeft = RuleChecks.table(nearMissBuilder, 1, false);
        var nearRight = RuleChecks.table(nearMissBuilder, 2, false);
        var nearLeftScan = nearMissBuilder.scan(nearLeft).build();
        var nearRightScan = nearMissBuilder.scan(nearRight).build();
        nearMissBuilder.push(nearLeftScan);
        nearMissBuilder.push(nearRightScan);
        var nearCondition = RuleChecks.eq(nearMissBuilder, CalciteRuntime.field(nearMissBuilder, 2, 0),
                CalciteRuntime.field(nearMissBuilder, 2, 1));
        RelNode nearMiss = org.apache.calcite.rel.logical.LogicalJoin.create(nearLeftScan, nearRightScan,
                java.util.List.of(), nearCondition, java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneSemiAntiJoinRightCols test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}

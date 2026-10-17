package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.Backends.Calcite.Generated.PushLeakproofJoinIntoPermeableBarrierRight;
import org.qed.RuleBuilder;
import org.qed.RRule;

public class PushLeakproofJoinIntoPermeableBarrierRightTest {
    public static void runTest() {
        RRule rule = new org.qed.RRuleInstances.PushLeakproofJoinIntoPermeableBarrierRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 2, false, false);
        var r = RuleChecks.table(builder, 2, false, false);
        var left = builder.scan(l).build();
        var right = builder.scan(r).build();
        var barrier = org.apache.calcite.rel.logical.LogicalProject.create(right,
                java.util.List.of(),
                java.util.List.of(builder.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 0),
                        builder.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(1).getType(), 1)),
                (java.util.List<String>) null);
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                builder.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                builder.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 2));
        var before = org.apache.calcite.rel.logical.LogicalJoin.create(left, barrier, java.util.List.of(),
                condition, java.util.Set.of(), JoinRelType.INNER);
        var plain = RuleChecks.ruleBuilder(builder);
        var expectedLeft = plain.scan(l).build();
        var expectedRight = plain.scan(r).build();
        var expectedCondition = plain.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                plain.getRexBuilder().makeInputRef(expectedLeft.getRowType().getFieldList().get(0).getType(), 0),
                plain.getRexBuilder().makeInputRef(expectedLeft.getRowType().getFieldList().get(0).getType(), 2));
        var expectedJoin = org.apache.calcite.rel.logical.LogicalJoin.create(expectedLeft, expectedRight,
                java.util.List.of(), expectedCondition, java.util.Set.of(), JoinRelType.INNER);
        var expected = org.apache.calcite.rel.logical.LogicalProject.create(expectedJoin,
                java.util.List.of(), expectedJoin.getRowType().getFieldList().stream()
                        .map(field -> plain.getRexBuilder().makeCast(before.getRowType().getFieldList()
                                        .get(field.getIndex()).getType(), plain.getRexBuilder()
                                        .makeInputRef(field.getType(), field.getIndex()), true)).toList(),
                (java.util.List<String>) null);
        RuleChecks.rewrites(rule, before, expected);
        var nonInner = org.apache.calcite.rel.logical.LogicalJoin.create(left, barrier, java.util.List.of(),
                condition, java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, nonInner);
    }

    public static void main(String[] args) {
        System.out.println("Running PushLeakproofJoinIntoPermeableBarrierRight test...");
        runTest();
    }
}

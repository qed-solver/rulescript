package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.HoistJoinSubquery;

public final class HoistJoinSubqueryTest {
    public static void runTest() {
        var rule = new HoistJoinSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltUncorrelatedExistsMovesToRightFilter();
        handBuiltCorrelatedExistsRemainsInJoinCondition();
    }

    private static void handBuiltUncorrelatedExistsMovesToRightFilter() {
        var builder = org.qed.RuleBuilder.create();
        var lName = RuleChecks.table(builder, 1, false);
        var rName = RuleChecks.table(builder, 1, false);
        var sName = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(lName).build();
        var right = b.scan(rName).build();
        var sub = b.scan(sName).build();
        var rex = b.getRexBuilder();
        var on = RuleChecks.eq(b, rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));
        var subPredicate = RuleChecks.gt(b, rex.makeInputRef(sub.getRowType().getFieldList().get(0).getType(), 0), 10);
        var exists = org.apache.calcite.rex.RexSubQuery.exists(
                org.apache.calcite.rel.logical.LogicalFilter.create(sub, subPredicate));
        var before = org.apache.calcite.rel.logical.LogicalJoin.create(left, right, java.util.List.of(),
                rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND, on, exists), java.util.Set.of(),
                JoinRelType.INNER);
        var expectedRight = org.apache.calcite.rel.logical.LogicalFilter.create(right, exists);
        var expected = org.apache.calcite.rel.logical.LogicalJoin.create(left, expectedRight, java.util.List.of(),
                on, java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(new HoistJoinSubquery(), before, expected);
    }

    private static void handBuiltCorrelatedExistsRemainsInJoinCondition() {
        var builder = org.qed.RuleBuilder.create();
        var lName = RuleChecks.table(builder, 1, false);
        var rName = RuleChecks.table(builder, 1, false);
        var sName = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(lName).build();
        var right = b.scan(rName).build();
        var sub = b.scan(sName).build();
        var rex = b.getRexBuilder();
        var on = RuleChecks.eq(b, rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));
        var correlationId = b.getCluster().createCorrel();
        var corr = rex.makeCorrel(left.getRowType(), correlationId);
        var correlatedPredicate = RuleChecks.eq(b, rex.makeFieldAccess(corr, 0),
                rex.makeInputRef(sub.getRowType().getFieldList().get(0).getType(), 0));
        var exists = org.apache.calcite.rex.RexSubQuery.exists(
                org.apache.calcite.rel.logical.LogicalFilter.create(sub, correlatedPredicate));
        var before = org.apache.calcite.rel.logical.LogicalJoin.create(left, right, java.util.List.of(),
                rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND, on, exists), java.util.Set.of(),
                JoinRelType.INNER);
        RuleChecks.doesNotApply(new HoistJoinSubquery(), before);
    }

    public static void main(String[] args) {
        System.out.println("Running HoistJoinSubquery test...");
        runTest();
    }
}

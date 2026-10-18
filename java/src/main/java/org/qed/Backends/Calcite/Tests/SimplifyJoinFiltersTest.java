package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyJoinFilters;
import org.qed.RuleBuilder;

public final class SimplifyJoinFiltersTest {
    public static void runTest() {
        var rule = new SimplifyJoinFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltInnerJoin(rule);
        rejectsOuterJoin();
    }

    private static void handBuiltInnerJoin(SimplifyJoinFilters rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, true);
        var rightName = RuleChecks.table(builder, 1, false, true);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        var left = relBuilder.scan(leftName).build();
        var right = relBuilder.scan(rightName).build();
        var rex = relBuilder.getRexBuilder();
        var equality = rex.makeCall(SqlStdOperatorTable.EQUALS,
                rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), left.getRowType().getFieldCount()));
        var condition = rex.makeCall(SqlStdOperatorTable.AND, equality, rex.makeLiteral(true));
        var before = LogicalJoin.create(left, right, java.util.List.of(), condition, java.util.Set.of(), JoinRelType.INNER);
        var expected = LogicalJoin.create(left, right, java.util.List.of(), equality, java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsOuterJoin() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, false);
        var rightName = RuleChecks.table(builder, 1, false, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        var left = relBuilder.scan(leftName).build();
        var right = relBuilder.scan(rightName).build();
        var rex = relBuilder.getRexBuilder();
        var condition = rex.makeCall(SqlStdOperatorTable.AND,
                rex.makeCall(SqlStdOperatorTable.EQUALS, rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                        rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1)),
                rex.makeLiteral(true));
        var outer = LogicalJoin.create(left, right, java.util.List.of(), condition, java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(new SimplifyJoinFilters(), outer);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyJoinFilters test...");
        runTest();
    }
}

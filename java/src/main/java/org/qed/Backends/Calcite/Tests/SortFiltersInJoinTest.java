package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SortFiltersInJoin;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

public class SortFiltersInJoinTest {
    public static void runTest() {
        var rule = new SortFiltersInJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltJoin();
        rejectsNonInnerJoin();
    }

    private static void rewritesHandBuiltJoin() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, false);
        var rightName = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        RelNode left = b.scan(leftName).build();
        RelNode right = b.scan(rightName).build();
        var rex = b.getRexBuilder();
        var left0 = rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var right0 = rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        RexNode first = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, left0, right0);
        RexNode second = rex.makeCall(SqlStdOperatorTable.LESS_THAN, left0, right0);
        var before = LogicalJoin.create(left, right, List.of(),
                rex.makeCall(SqlStdOperatorTable.AND, first, second), Set.of(), JoinRelType.INNER);
        var expected = LogicalJoin.create(left, right, List.of(),
                rex.makeCall(SqlStdOperatorTable.AND, second, first), Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(new SortFiltersInJoin(), before, expected);
    }

    private static void rejectsNonInnerJoin() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, false);
        var rightName = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        RelNode left = b.scan(leftName).build();
        RelNode right = b.scan(rightName).build();
        var rex = b.getRexBuilder();
        var left0 = rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var right0 = rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        RexNode first = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, left0, right0);
        RexNode second = rex.makeCall(SqlStdOperatorTable.LESS_THAN, left0, right0);
        var plan = LogicalJoin.create(left, right, List.of(),
                rex.makeCall(SqlStdOperatorTable.AND, first, second), Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(new SortFiltersInJoin(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running SortFiltersInJoin test...");
        runTest();
    }
}

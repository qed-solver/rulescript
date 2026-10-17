package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FilterJoin;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

public final class FilterJoinTest {
    public static void runTest() {
        System.out.println("Running FilterJoin test...");
        var rule = new FilterJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsOuterJoin(rule);
    }

    private static void rewritesHandBuiltPlan(FilterJoin rule) {
        var tables = RuleBuilder.create();
        var leftName = RuleChecks.table(tables, 1, false);
        var rightName = RuleChecks.table(tables, 1, false);
        var builder = RuleChecks.ruleBuilder(tables);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var rex = builder.getRexBuilder();
        var leftRef = rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var rightRef = rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        var joinCondition = rex.makeCall(SqlStdOperatorTable.EQUALS, leftRef, rightRef);
        var aboveFilter = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, leftRef, rex.makeExactLiteral(java.math.BigDecimal.TEN));
        var before = LogicalFilter.create(LogicalJoin.create(left, right, List.of(), joinCondition, Set.of(),
                JoinRelType.INNER), aboveFilter);
        RexNode expectedCondition = rex.makeCall(SqlStdOperatorTable.AND, joinCondition, aboveFilter);
        var expected = LogicalJoin.create(left, right, List.of(), expectedCondition, Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsOuterJoin(FilterJoin rule) {
        var tables = RuleBuilder.create();
        var leftName = RuleChecks.table(tables, 1, false);
        var rightName = RuleChecks.table(tables, 1, false);
        var builder = RuleChecks.ruleBuilder(tables);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var rex = builder.getRexBuilder();
        var leftRef = rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var rightRef = rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        var joinCondition = rex.makeCall(SqlStdOperatorTable.EQUALS, leftRef, rightRef);
        var aboveFilter = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, leftRef, rex.makeExactLiteral(java.math.BigDecimal.TEN));
        var noMatch = LogicalFilter.create(LogicalJoin.create(left, right, List.of(), joinCondition, Set.of(),
                JoinRelType.LEFT), aboveFilter);
        RuleChecks.doesNotApply(rule, noMatch);
    }

    public static void main(String[] args) {
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("FilterJoin test failed with " + CalciteTester.failures() + " failure(s)");
        }
    }
}

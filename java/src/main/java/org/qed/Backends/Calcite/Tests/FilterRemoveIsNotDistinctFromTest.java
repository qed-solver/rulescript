package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FilterRemoveIsNotDistinctFrom;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

public final class FilterRemoveIsNotDistinctFromTest {
    public static void runTest() {
        var rule = new FilterRemoveIsNotDistinctFrom();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsOrdinaryEquality(rule);
    }

    private static void rewritesHandBuiltPlan(FilterRemoveIsNotDistinctFrom rule) {
        var tables = RuleBuilder.create();
        var leftName = RuleChecks.table(tables, 1, false);
        var rightName = RuleChecks.table(tables, 1, false);
        var builder = RuleChecks.ruleBuilder(tables);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var rex = builder.getRexBuilder();
        var leftRef = rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var rightRef = rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        var beforeCondition = rex.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, leftRef, rightRef);
        var before = LogicalFilter.create(LogicalJoin.create(left, right, List.of(),
                rex.makeLiteral(true), Set.of(), JoinRelType.INNER), beforeCondition);
        var nullsEqual = rex.makeCall(SqlStdOperatorTable.AND,
                rex.makeCall(SqlStdOperatorTable.IS_NULL, leftRef),
                rex.makeCall(SqlStdOperatorTable.IS_NULL, rightRef));
        RexNode equalAndTrue = rex.makeCall(SqlStdOperatorTable.IS_TRUE,
                rex.makeCall(SqlStdOperatorTable.EQUALS, leftRef, rightRef));
        var expectedCondition = rex.makeCall(SqlStdOperatorTable.OR, nullsEqual, equalAndTrue);
        var expected = LogicalFilter.create(LogicalJoin.create(left, right, List.of(),
                rex.makeLiteral(true), Set.of(), JoinRelType.INNER), expectedCondition);
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsOrdinaryEquality(FilterRemoveIsNotDistinctFrom rule) {
        var tables = RuleBuilder.create();
        var leftName = RuleChecks.table(tables, 1, false);
        var rightName = RuleChecks.table(tables, 1, false);
        var builder = RuleChecks.ruleBuilder(tables);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var rex = builder.getRexBuilder();
        var leftRef = rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var rightRef = rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        var condition = rex.makeCall(SqlStdOperatorTable.EQUALS, leftRef, rightRef);
        var plan = LogicalFilter.create(LogicalJoin.create(left, right, List.of(),
                rex.makeLiteral(true), Set.of(), JoinRelType.INNER), condition);
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running FilterRemoveIsNotDistinctFrom test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError(CalciteTester.failures() + " test failure(s)");
        }
    }
}

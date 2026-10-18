package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRule;
import org.qed.RuleBuilder;

public final class HoistSelectExistsTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.HoistSelectExists();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsNearMissWithoutExists(rule);
    }

    private static void rewritesHandBuiltPlan(RRule rule) {
        var builder = RuleBuilder.create();
        var leftTable = RuleChecks.table(builder, 1, false, false);
        var subTable = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        RelNode left = b.scan(leftTable).build();
        RelNode sub = b.scan(subTable).build();
        var subCondition = b.call(SqlStdOperatorTable.GREATER_THAN, b.getRexBuilder().makeInputRef(sub.getRowType(), 0), b.literal(10));
        RelNode filteredSub = LogicalFilter.create(sub, subCondition);
        var exists = RexSubQuery.exists(filteredSub);
        var rest = b.call(SqlStdOperatorTable.LESS_THAN, b.getRexBuilder().makeInputRef(left.getRowType(), 0), b.literal(100));
        var beforeCondition = b.call(SqlStdOperatorTable.AND, exists, rest);
        RelNode before = LogicalFilter.create(left, beforeCondition);

        var expectedBuilder = RuleBuilder.create();
        var expectedLeftTable = RuleChecks.table(expectedBuilder, 1, false, false);
        var expectedSubTable = RuleChecks.table(expectedBuilder, 1, false, false);
        var e = RuleChecks.ruleBuilder(expectedBuilder);
        RelNode expectedLeft = e.scan(expectedLeftTable).build();
        RelNode expectedSub = e.scan(expectedSubTable).build();
        var expectedSubCondition = e.call(SqlStdOperatorTable.GREATER_THAN, e.getRexBuilder().makeInputRef(expectedSub.getRowType(), 0), e.literal(10));
        RelNode expectedFilteredSub = LogicalFilter.create(expectedSub, expectedSubCondition);
        var expectedRest = e.call(SqlStdOperatorTable.LESS_THAN, e.getRexBuilder().makeInputRef(expectedLeft.getRowType(), 0), e.literal(100));
        RelNode semi = e.push(expectedLeft).push(expectedFilteredSub).join(JoinRelType.SEMI, e.literal(true)).build();
        RelNode expected = LogicalFilter.create(semi, expectedRest);
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsNearMissWithoutExists(RRule rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        RelNode input = b.scan(table).build();
        var condition = b.call(SqlStdOperatorTable.LESS_THAN, b.getRexBuilder().makeInputRef(input.getRowType(), 0), b.literal(100));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(input, condition));
    }

    public static void main(String[] args) {
        System.out.println("Running HoistSelectExists test...");
        runTest();
    }
}

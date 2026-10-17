package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.Tests.RuleChecks;
import org.qed.RRuleInstances.FilterMultiJoinMerge;
import org.qed.RuleBuilder;

import java.util.List;

public final class FilterMultiJoinMergeTest {
    public static void runTest() {
        var rule = new FilterMultiJoinMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsNonInnerJoin();
    }

    private static void rewritesHandBuiltPlan(FilterMultiJoinMerge rule) {
        var builder = RuleBuilder.create();
        var a = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.table(builder, 1, false);
        var c = RuleChecks.table(builder, 1, false);
        var rel = RuleChecks.ruleBuilder(builder);
        var aScan = rel.scan(a).build();
        var bScan = rel.scan(b).build();
        var cScan = rel.scan(c).build();
        var rex = rel.getRexBuilder();
        var ab = LogicalJoin.create(aScan, bScan, List.of(), rex.makeLiteral(true), java.util.Set.of(),
                JoinRelType.INNER);
        var row = new RexNode[] {rex.makeInputRef(ab, 0), rex.makeInputRef(ab, 1)};
        var post = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, row[0], rel.literal(4));
        var abc = LogicalJoin.create(ab, cScan, List.of(), post, java.util.Set.of(), JoinRelType.INNER);
        var outerCondition = rex.makeCall(SqlStdOperatorTable.LESS_THAN, rex.makeInputRef(abc, 1), rel.literal(9));
        var before = LogicalFilter.create(abc, outerCondition);
        var expectedJoin = LogicalJoin.create(ab, cScan, List.of(), rex.makeCall(SqlStdOperatorTable.AND, post,
                outerCondition), java.util.Set.of(), JoinRelType.INNER);
        var expected = expectedJoin;
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsNonInnerJoin() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false);
        var rightName = RuleChecks.table(builder, 1, false);
        var rel = RuleChecks.ruleBuilder(builder);
        var left = rel.scan(leftName).build();
        var right = rel.scan(rightName).build();
        var rex = rel.getRexBuilder();
        var condition = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, rex.makeInputRef(left, 0), rel.literal(0));
        RelNode outer = LogicalJoin.create(left, right, List.of(), rex.makeLiteral(true), java.util.Set.of(),
                JoinRelType.LEFT);
        RelNode filtered = LogicalFilter.create(outer, condition);
        RuleChecks.doesNotApply(new FilterMultiJoinMerge(), filtered);
    }

    public static void main(String[] args) {
        System.out.println("Running FilterMultiJoinMerge test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.TryDecorrelateInnerLeftJoin;

import java.util.List;

public class TryDecorrelateInnerLeftJoinTest {
    public static void runTest() {
        var rule = new TryDecorrelateInnerLeftJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        independentConcreteRewrite(rule);
        rejectsDifferentJoinType(rule);
    }

    private static void independentConcreteRewrite(TryDecorrelateInnerLeftJoin rule) {
        // Build all three scans in one schema so the independent plans share compatible row types.
        var schema = org.qed.RuleBuilder.create();
        var l = RuleChecks.table(schema, 1, false);
        var il = RuleChecks.table(schema, 1, false);
        var ir = RuleChecks.table(schema, 1, false);
        var b = RuleChecks.ruleBuilder(schema);

        var left = b.scan(l).build();
        var innerLeft = b.scan(il).build();
        var innerRight = b.scan(ir).build();
        var rb = b.getRexBuilder();
        var innerCond = rb.makeCall(SqlStdOperatorTable.GREATER_THAN,
                rb.makeInputRef(innerLeft.getRowType().getFieldList().get(0).getType(), 0),
                rb.makeInputRef(innerRight.getRowType().getFieldList().get(0).getType(), 1));

        var beforeRight = LogicalJoin.create(innerLeft, innerRight, List.of(), innerCond,
                java.util.Set.of(), JoinRelType.LEFT);
        var beforeCond = rb.makeCall(SqlStdOperatorTable.EQUALS,
                rb.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                rb.makeInputRef(beforeRight.getRowType().getFieldList().get(0).getType(), 1));
        var before = LogicalJoin.create(left, beforeRight, List.of(), beforeCond,
                java.util.Set.of(), JoinRelType.INNER);

        var afterLeftCond = rb.makeCall(SqlStdOperatorTable.EQUALS,
                rb.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                rb.makeInputRef(innerLeft.getRowType().getFieldList().get(0).getType(), 1));
        var afterLeft = LogicalJoin.create(left, innerLeft, List.of(), afterLeftCond,
                java.util.Set.of(), JoinRelType.INNER);
        var afterCond = rb.makeCall(SqlStdOperatorTable.GREATER_THAN,
                rb.makeInputRef(afterLeft.getRowType().getFieldList().get(1).getType(), 1),
                rb.makeInputRef(innerRight.getRowType().getFieldList().get(0).getType(), 2));
        var expected = LogicalJoin.create(afterLeft, innerRight, List.of(), afterCond,
                java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsDifferentJoinType(TryDecorrelateInnerLeftJoin rule) {
        var bld = org.qed.RuleBuilder.create();
        var l = RuleChecks.table(bld, 1, false);
        var il = RuleChecks.table(bld, 1, false);
        var ir = RuleChecks.table(bld, 1, false);
        var b = RuleChecks.ruleBuilder(bld);
        var left = b.scan(l).build();
        var innerLeft = b.scan(il).build();
        var innerRight = b.scan(ir).build();
        var cond = b.getRexBuilder().makeLiteral(true);
        var inner = LogicalJoin.create(innerLeft, innerRight, List.of(), cond,
                java.util.Set.of(), JoinRelType.LEFT);
        var near = LogicalJoin.create(left, inner, List.of(), cond,
                java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, near);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateInnerLeftJoin test...");
        runTest();
    }
}

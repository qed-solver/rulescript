package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.FoldGroupByAndWindow;

public class FoldGroupByAndWindowTest {
    public static void runTest() {
        var rule = new FoldGroupByAndWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive(rule);
        rejectsChangedGroupKey(rule);
    }

    private static void handBuiltPositive(FoldGroupByAndWindow rule) {
        var builder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var b = builder.transform(CalciteRuntime::configure);
        RelNode input = b.scan(table).build();
        RelNode window = aggregate(b, input, "w");
        RelNode joined = join(b, input, window);
        RelNode before = aggregate(b, joined, "a");
        RelNode expected = aggregate(b, input, "a");
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsChangedGroupKey(FoldGroupByAndWindow rule) {
        var b = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(b, 2, false, false);
        var rb = b.transform(CalciteRuntime::configure);
        RelNode input = rb.scan(table).build();
        RelNode window = aggregate(rb, input, "w");
        RelNode joined = join(rb, input, window);
        RelNode nearMiss = LogicalAggregate.create(joined, java.util.List.of(), ImmutableBitSet.of(1), null,
                java.util.List.of(agg(joined, "a")));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RelNode aggregate(org.apache.calcite.tools.RelBuilder b, RelNode input, String name) {
        return LogicalAggregate.create(input, java.util.List.of(), ImmutableBitSet.of(0), null,
                java.util.List.of(agg(input, name)));
    }

    private static org.apache.calcite.rel.core.AggregateCall agg(RelNode input, String name) {
        return org.apache.calcite.rel.core.AggregateCall.create(SqlStdOperatorTable.SUM, false, false, false,
                java.util.List.of(1), -1, null, RelCollations.EMPTY, 1, input, null, name);
    }

    private static RelNode join(org.apache.calcite.tools.RelBuilder b, RelNode left, RelNode right) {
        RexNode leftKey = b.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        RexNode rightKey = b.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 2);
        return LogicalJoin.create(left, right, java.util.List.of(), RuleChecks.eq(b, leftKey, rightKey),
                java.util.Set.of(), JoinRelType.INNER);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldGroupByAndWindow test...");
        runTest();
    }
}

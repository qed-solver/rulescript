package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.TryDecorrelateWindow;
import org.qed.RelType;
import org.qed.RuleBuilder;

public final class TryDecorrelateWindowTest {
    public static void runTest() {
        var rule = new TryDecorrelateWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        handBuiltMatch();
        meaningfulNoMatch();
    }

    private static void handBuiltMatch() {
        var raw = RuleBuilder.create();
        var leftName = RuleChecks.table(raw, 1, true, false);
        var inputName = RuleChecks.table(raw, 1, false, false);
        var b = RuleChecks.ruleBuilder(raw);
        var left = b.scan(leftName).build();
        var input = b.scan(inputName).build();

        var wholeInputAgg = b.push(input).aggregate(b.groupKey(),
                b.aggregateCall(RuleBuilder.create().genericAggregateOp("w", RelType.fromString("INTEGER", false)), false,
                        null, "w", ref(b, input, 0))).build();
        var window = b.push(input).push(wholeInputAgg).join(JoinRelType.INNER, b.literal(true)).build();
        var on = b.call(RuleBuilder.create().genericPredicateOp("on_cond", true),
                ref(b, window, 0), ref(b, window, 1));
        var before = b.push(left).push(window).join(JoinRelType.INNER, on).build();

        // Build the expected plan independently as cross join, partitioned
        // aggregate, join-back, post-window predicate, and output projection.
        b.clear();
        var l2 = b.scan(leftName).build();
        var i2 = b.scan(inputName).build();
        var cross = b.push(l2).push(i2).join(JoinRelType.INNER, b.literal(true)).build();
        var partitionAgg = b.push(cross).aggregate(b.groupKey(0),
                b.aggregateCall(RuleBuilder.create().genericAggregateOp("w", RelType.fromString("INTEGER", false)), false,
                        null, "w", ref(b, cross, 1))).build();
        var augmented = b.push(cross).push(partitionAgg).join(JoinRelType.INNER,
                b.call(SqlStdOperatorTable.EQUALS, ref(b, cross, 0),
                        b.getRexBuilder().makeInputRef(cross.getRowType().getFieldList().get(0).getType(), 2))).build();
        var post = b.push(augmented).filter(b.call(RuleBuilder.create().genericPredicateOp("on_cond", true),
                ref(b, augmented, 0), ref(b, augmented, 1))).project(ref(b, augmented, 0), ref(b, augmented, 1), ref(b, augmented, 3)).build();
        RuleChecks.rewrites(new TryDecorrelateWindow(), before, post);
    }

    private static org.apache.calcite.rex.RexNode ref(org.apache.calcite.tools.RelBuilder b, RelNode input, int index) {
        return b.getRexBuilder().makeInputRef(input.getRowType().getFieldList().get(index).getType(), index);
    }

    private static void meaningfulNoMatch() {
        var raw = RuleBuilder.create();
        var table = RuleChecks.table(raw, 1, false, false);
        var b = RuleChecks.ruleBuilder(raw);
        RelNode scan = b.scan(table).build();
        RuleChecks.doesNotApply(new TryDecorrelateWindow(), scan);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateWindow test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterCorrelate;
import org.qed.RuleBuilder;

public final class FilterCorrelateTest {
    public static void runTest() {
        var rule = new FilterCorrelate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltRewrite(rule);
        rejectsNonTrueJoinCondition(rule);
    }

    private static void handBuiltRewrite(FilterCorrelate rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false);
        var rightName = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var joined = b.push(left).push(right).join(JoinRelType.INNER, b.literal(true)).build();
        var leftPredicate = gt(b, ref(b, joined, 0), 10);
        var rightPredicate = gt(b, ref(b, joined, 1), 20);
        var bothPredicate = eq(b, ref(b, joined, 0), ref(b, joined, 1));
        RelNode before = LogicalFilter.create(joined, b.and(leftPredicate, rightPredicate, bothPredicate));

        var expectedBuilder = b;
        var expectedLeft = expectedBuilder.push(left).filter(gt(expectedBuilder, expectedBuilder.field(0), 10)).build();
        var expectedRight = expectedBuilder.push(right).filter(gt(expectedBuilder, expectedBuilder.field(0), 20)).build();
        var expectedJoin = expectedBuilder.push(expectedLeft).push(expectedRight)
                .join(JoinRelType.INNER, expectedBuilder.literal(true)).build();
        RelNode after = LogicalFilter.create(expectedJoin,
                eq(expectedBuilder, ref(expectedBuilder, expectedJoin, 0), ref(expectedBuilder, expectedJoin, 1)));
        RuleChecks.rewrites(rule, before, after);
    }

    private static void rejectsNonTrueJoinCondition(FilterCorrelate rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false);
        var rightName = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var joined = b.push(left).push(right)
                .join(JoinRelType.INNER, eq(b, b.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                        b.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1))).build();
        var condition = b.and(gt(b, ref(b, joined, 0), 10), gt(b, ref(b, joined, 1), 20),
                eq(b, ref(b, joined, 0), ref(b, joined, 1)));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(joined, condition));
    }

    private static org.apache.calcite.rex.RexNode gt(RelBuilder b, org.apache.calcite.rex.RexNode x, int n) {
        return b.call(SqlStdOperatorTable.GREATER_THAN, x, b.literal(n));
    }

    private static org.apache.calcite.rex.RexNode eq(RelBuilder b, org.apache.calcite.rex.RexNode x,
                                                      org.apache.calcite.rex.RexNode y) {
        return b.call(SqlStdOperatorTable.EQUALS, x, y);
    }

    private static org.apache.calcite.rex.RexNode ref(RelBuilder b, RelNode input, int index) {
        var field = input.getRowType().getFieldList().get(index);
        return b.getRexBuilder().makeInputRef(field.getType(), index);
    }

    public static void main(String[] args) {
        System.out.println("Running FilterCorrelate test...");
        runTest();
    }
}

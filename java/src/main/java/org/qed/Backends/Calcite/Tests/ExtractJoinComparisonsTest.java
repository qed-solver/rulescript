package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRuleInstances.ExtractJoinComparisons;
import org.qed.RuleBuilder;

import java.lang.reflect.Field;
import java.util.List;

public final class ExtractJoinComparisonsTest {
    public static void runTest() {
        var rule = new ExtractJoinComparisons();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch(rule);
        meaningfulNoMatch(rule);
    }

    private static void handBuiltMatch(ExtractJoinComparisons rule) {
        var b = builder();
        var left = b.scan(RuleChecks.table(b, 1, false));
        var leftInput = b.build();
        var right = b.scan(RuleChecks.table(b, 1, false));
        var rightInput = b.build();
        var f = operator("fOp");
        var g = operator("gOp");
        var cmp = operator("cmpOp");

        b.push(leftInput).push(rightInput);
        var condition = b.call(cmp, b.call(f, joinInput(b, leftInput, rightInput, 0)),
                b.call(g, joinInput(b, leftInput, rightInput, 1)));
        var inputPlan = b.join(JoinRelType.INNER, condition).build();

        b.push(leftInput);
        var widenedLeft = b.project(List.of(b.field(0), b.call(f, b.field(0))), List.of(), true).build();
        b.push(rightInput);
        var widenedRight = b.project(List.of(b.field(0), b.call(g, b.field(0))), List.of(), true).build();
        b.push(widenedLeft).push(widenedRight);
        var expectedCondition = b.call(cmp, joinInput(b, widenedLeft, widenedRight, 1),
                joinInput(b, widenedLeft, widenedRight, 3));
        var joined = b.join(JoinRelType.INNER, expectedCondition).build();
        var expected = b.push(joined).project(List.of(input(b, joined, 0), input(b, joined, 2)), List.of(), true).build();
        RuleChecks.rewrites(rule, inputPlan, expected);
    }

    private static void meaningfulNoMatch(ExtractJoinComparisons rule) {
        var b = builder();
        var left = b.scan(RuleChecks.table(b, 1, false)).build();
        var right = b.scan(RuleChecks.table(b, 1, false)).build();
        var f = operator("fOp");
        var cmp = operator("cmpOp");
        b.push(left).push(right);
        RexNode condition = b.call(cmp, b.call(f, joinInput(b, left, right, 0)),
                b.call(f, joinInput(b, left, right, 0)));
        var input = b.join(JoinRelType.INNER, condition).build();
        RuleChecks.doesNotApply(rule, input);
    }

    private static RuleBuilder builder() {
        return RuleBuilder.create();
    }

    private static RexNode joinInput(RuleBuilder b, RelNode left, RelNode right, int index) {
        var type = index < left.getRowType().getFieldCount()
                ? left.getRowType().getFieldList().get(index).getType()
                : right.getRowType().getFieldList().get(index - left.getRowType().getFieldCount()).getType();
        return b.getRexBuilder().makeInputRef(type, index);
    }

    private static RexNode input(RuleBuilder b, RelNode rel, int index) {
        return b.getRexBuilder().makeInputRef(rel.getRowType().getFieldList().get(index).getType(), index);
    }

    private static SqlOperator operator(String name) {
        try {
            Field field = ExtractJoinComparisons.class.getDeclaredField(name);
            field.setAccessible(true);
            return (SqlOperator) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) {
        System.out.println("Running ExtractJoinComparisons test...");
        runTest();
    }
}

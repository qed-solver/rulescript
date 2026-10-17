package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.InjectRuntimeFilter;
import org.qed.RuleBuilder;

public final class InjectRuntimeFilterTest {
    public static void runTest() {
        var rule = new InjectRuntimeFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch(rule);
        meaningfulNoMatch(rule);
    }

    private static void handBuiltMatch(InjectRuntimeFilter rule) {
        var schema = RuleBuilder.create();
        var b = RuleChecks.ruleBuilder(schema);
        var left = b.scan(RuleChecks.table(schema, 1, false)).build();
        var right = b.scan(RuleChecks.table(schema, 1, false)).build();
        var before = b.push(left).push(right)
                .join(JoinRelType.INNER, RuleChecks.eq(b, joinInput(b, left, right, 0),
                        joinInput(b, left, right, 1))).build();

        var expectedLeft = b.push(left).push(right)
                .join(JoinRelType.SEMI, RuleChecks.eq(b, joinInput(b, left, right, 0),
                        joinInput(b, left, right, 1))).build();
        var expected = b.push(expectedLeft).push(right)
                .join(JoinRelType.INNER, RuleChecks.eq(b, joinInput(b, expectedLeft, right, 0),
                        joinInput(b, expectedLeft, right, 1))).build();
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void meaningfulNoMatch(InjectRuntimeFilter rule) {
        var schema = RuleBuilder.create();
        var b = RuleChecks.ruleBuilder(schema);
        var left = b.scan(RuleChecks.table(schema, 1, false)).build();
        var right = b.scan(RuleChecks.table(schema, 1, false)).build();
        var before = b.push(left).push(right)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, joinInput(b, left, right, 0),
                        joinInput(b, left, right, 1))).build();
        RuleChecks.doesNotApply(rule, before);
    }

    private static RexNode joinInput(RelBuilder b, RelNode left, RelNode right, int index) {
        var leftCount = left.getRowType().getFieldCount();
        var type = index < leftCount ? left.getRowType().getFieldList().get(index).getType()
                : right.getRowType().getFieldList().get(index - leftCount).getType();
        return b.getRexBuilder().makeInputRef(type, index);
    }

    public static void main(String[] args) {
        System.out.println("Running InjectRuntimeFilter test...");
        runTest();
    }
}

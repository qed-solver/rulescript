package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.JoinPushExpressions;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public final class JoinPushExpressionsTest {
    public static void runTest() {
        var rule = new JoinPushExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltRewrite(rule);
        rejectsWrongJoinType(rule);
    }

    private static void handBuiltRewrite(JoinPushExpressions rule) {
        var builder = RuleBuilder.create();
        var l = table(builder, 1, false);
        var r = table(builder, 1, false);
        var b = ruleBuilder(builder);
        b.scan(l).scan(r);
        var left = b.field(2, 0, 0);
        var right = b.field(2, 1, 0);
        var fl = builder.genericProjectionOp("FL", org.qed.RelType.fromString("INTEGER", true));
        var fr = builder.genericProjectionOp("FR", org.qed.RelType.fromString("INTEGER", true));
        var eq = builder.genericPredicateOp("EQ", true);
        var residual = builder.genericPredicateOp("RES", true);
        var condition = b.and(b.call(eq, b.call(fl, left), b.call(fr, right)), b.call(residual, left, right));
        RelNode before = b.join(JoinRelType.INNER, condition).build();

        var expectedBuilder = ruleBuilder(builder);
        var expectedLeft = expectedBuilder.scan(l)
                .project(expectedBuilder.field(0), expectedBuilder.call(fl, expectedBuilder.field(0))).build();
        var expectedRight = expectedBuilder.scan(r)
                .project(expectedBuilder.field(0), expectedBuilder.call(fr, expectedBuilder.field(0))).build();
        var rex = expectedBuilder.getRexBuilder();
        var joinType = expectedBuilder.getTypeFactory().builder()
                .addAll(expectedLeft.getRowType().getFieldList())
                .addAll(expectedRight.getRowType().getFieldList()).build();
        var eqCond = expectedBuilder.call(eq,
                rex.makeInputRef(joinType.getFieldList().get(1).getType(), 1),
                rex.makeInputRef(joinType.getFieldList().get(3).getType(), 3));
        var residualCond = expectedBuilder.call(residual,
                rex.makeInputRef(joinType.getFieldList().get(0).getType(), 0),
                rex.makeInputRef(joinType.getFieldList().get(2).getType(), 2));
        var joined = expectedBuilder.push(expectedLeft).push(expectedRight)
                .join(JoinRelType.INNER, expectedBuilder.and(eqCond, residualCond)).build();
        RelNode after = expectedBuilder.push(joined).project(
                rex.makeInputRef(joined.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(joined.getRowType().getFieldList().get(2).getType(), 2)).build();
        rewrites(rule, before, after);
    }

    private static void rejectsWrongJoinType(JoinPushExpressions rule) {
        var builder = RuleBuilder.create();
        var l = table(builder, 1, false);
        var r = table(builder, 1, false);
        var b = ruleBuilder(builder);
        b.scan(l).scan(r);
        var left = b.field(2, 0, 0);
        var right = b.field(2, 1, 0);
        var fl = builder.genericProjectionOp("FL", org.qed.RelType.fromString("INTEGER", true));
        var fr = builder.genericProjectionOp("FR", org.qed.RelType.fromString("INTEGER", true));
        var eq = builder.genericPredicateOp("EQ", true);
        var residual = builder.genericPredicateOp("RES", true);
        var condition = b.and(b.call(eq, b.call(fl, left), b.call(fr, right)), b.call(residual, left, right));
        doesNotApply(rule, b.join(JoinRelType.LEFT, condition).build());
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}

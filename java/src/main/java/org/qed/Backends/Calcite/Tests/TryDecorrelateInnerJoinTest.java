package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.TryDecorrelateInnerJoin;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public final class TryDecorrelateInnerJoinTest {
    public static void runTest() {
        var rule = new TryDecorrelateInnerJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltRewrite();
        rejectsOuterInnerJoin();
    }

    private static void handBuiltRewrite() {
        var l = RelRN.scan("L", "T");
        var il = RelRN.scan("IL", "T");
        var ir = RelRN.scan("IR", "T");
        var builder = RuleBuilder.create();
        var on = builder.genericPredicateOp("handOn", true);
        var innerOn = builder.genericPredicateOp("handInnerOn", true);
        var inner = il.join(JoinRelType.INNER, new RexRN.Pred(innerOn, il.joinFields(ir)), ir);
        var before = l.join(JoinRelType.INNER, new RexRN.Pred(on, l.joinFields(inner)), inner);

        var trueInner = il.join(JoinRelType.INNER, RexRN.trueLiteral(), ir);
        var expected = l.join(JoinRelType.INNER,
                RexRN.and(new RexRN.Pred(on, l.joinFields(trueInner)),
                        new RexRN.Pred(innerOn,
                                kala.collection.Seq.of(l.joinField(1, trueInner), l.joinField(2, trueInner)))),
                trueInner);
        // Use a separately assembled plan from the rule's specification and compare to its explicit rewrite.
        RuleChecks.rewrites(new TryDecorrelateInnerJoin(), FaithfulPlans.of(before), FaithfulPlans.expected(expected));
    }

    private static void rejectsOuterInnerJoin() {
        var left = RelRN.scan("L", "T");
        var il = RelRN.scan("IL", "T");
        var ir = RelRN.scan("IR", "T");
        var builder = RuleBuilder.create();
        var on = builder.genericPredicateOp("nearOn", true);
        var innerOn = builder.genericPredicateOp("nearInnerOn", true);
        var inner = il.join(JoinRelType.INNER, new RexRN.Pred(innerOn, il.joinFields(ir)), ir);
        var outerJoin = left.join(JoinRelType.LEFT, new RexRN.Pred(on, left.joinFields(inner)), inner);
        RuleChecks.doesNotApply(new TryDecorrelateInnerJoin(), FaithfulPlans.of(outerJoin));
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateInnerJoin test...");
        runTest();
    }
}

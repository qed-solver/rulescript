package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RRuleInstances.ReorderJoin;
import org.qed.RuleBuilder;
import kala.collection.Seq;

public class ReorderJoinTest {
    public static void main(String[] args) {
        System.out.println("Running ReorderJoin test...");
        runTest();
    }

    public static void runTest() {
        var rule = new ReorderJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ReorderJoin ignored) {
        var a = RelRN.scan("A", "A_Type");
        var b = RelRN.scan("B", "B_Type");
        var c = RelRN.scan("C", "C_Type");
        var d = RelRN.scan("D", "D_Type");
        var ops = RuleBuilder.create();
        SqlOperator pAB = ops.genericPredicateOp("pAB", true);
        SqlOperator pCD = ops.genericPredicateOp("pCD", true);
        SqlOperator qDBC = ops.genericPredicateOp("qDBC", true);
        var ad = a.join(JoinRelType.INNER, RexRN.trueLiteral(), d);
        var adc = ad.join(JoinRelType.INNER,
                new RexRN.Pred(pCD, Seq.of(ad.joinField(1, c), ad.joinField(2, c))), c);
        var before = adc.join(JoinRelType.INNER, RexRN.and(
                new RexRN.Pred(pAB, Seq.of(adc.joinField(0, b), adc.joinField(3, b))),
                new RexRN.Pred(qDBC, Seq.of(adc.joinField(1, b), adc.joinField(3, b), adc.joinField(2, b)))), b);
        var ab = a.join(JoinRelType.INNER,
                new RexRN.Pred(pAB, Seq.of(a.joinField(0, b), a.joinField(1, b))), b);
        var abd = ab.join(JoinRelType.INNER, RexRN.trueLiteral(), d);
        var afterJoin = abd.join(JoinRelType.INNER, RexRN.and(
                new RexRN.Pred(pCD, Seq.of(abd.joinField(2, c), abd.joinField(3, c))),
                new RexRN.Pred(qDBC, Seq.of(abd.joinField(2, c), abd.joinField(1, c), abd.joinField(3, c)))), c);
        var after = afterJoin.project(afterJoin.fields(0, 2, 3, 1));
        RuleChecks.rewrites(new ReorderJoin(), FaithfulPlans.concrete(before), FaithfulPlans.expectedConcrete(after));

        var nonInner = a.join(JoinRelType.LEFT, RexRN.trueLiteral(), d);
        var nonInnerRoot = nonInner.join(JoinRelType.INNER,
                new RexRN.Pred(pCD, Seq.of(nonInner.joinField(1, c), nonInner.joinField(2, c))), c);
        var nearMiss = nonInnerRoot.join(JoinRelType.INNER, RexRN.and(
                new RexRN.Pred(pAB, Seq.of(nonInnerRoot.joinField(0, b), nonInnerRoot.joinField(3, b))),
                new RexRN.Pred(qDBC, Seq.of(nonInnerRoot.joinField(1, b), nonInnerRoot.joinField(3, b), nonInnerRoot.joinField(2, b)))), b);
        RuleChecks.doesNotApply(new ReorderJoin(), FaithfulPlans.concrete(nearMiss));
    }
}

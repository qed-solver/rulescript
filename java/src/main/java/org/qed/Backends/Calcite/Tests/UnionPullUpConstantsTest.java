package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.qed.Backends.Calcite.Tests.RuleChecks;
import org.qed.RRuleInstances.UnionPullUpConstants;
import org.qed.RelRN;
import org.qed.RexRN;

public final class UnionPullUpConstantsTest {
    public static void runTest() {
        var rule = new UnionPullUpConstants();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        independentlyBuiltPlan();
        rejectsDistinctUnion();
    }

    private static void independentlyBuiltPlan() {
        var left = RelRN.scanMany("IndependentLeft", Seq.of(
                RexRN.varType("LeftKey", false), RexRN.varType("SharedValue", true)));
        var right = RelRN.scanMany("IndependentRight", Seq.of(
                RexRN.varType("RightKey", false), RexRN.varType("SharedValue", true)));
        var constant = RexRN.trueLiteral();
        var before = left.project(Seq.of(constant, left.field(1)))
                .union(true, right.project(Seq.of(constant, right.field(1))));
        var branchUnion = left.project(Seq.of(left.field(1)))
                .union(true, right.project(Seq.of(right.field(1))));
        var expected = branchUnion.project(Seq.of(constant, branchUnion.field(0)));
        RuleChecks.rewrites(new UnionPullUpConstants(), FaithfulPlans.of(before), FaithfulPlans.expected(expected));
    }

    private static void rejectsDistinctUnion() {
        var scan = RelRN.scanMany("DistinctInput", Seq.of(RexRN.varType("Value", true)));
        var projected = scan.project(Seq.of(RexRN.trueLiteral()));
        RuleChecks.doesNotApply(new UnionPullUpConstants(), FaithfulPlans.of(projected.union(false, projected)));
    }

    public static void main(String[] args) {
        System.out.println("Running UnionPullUpConstants test...");
        runTest();
    }
}

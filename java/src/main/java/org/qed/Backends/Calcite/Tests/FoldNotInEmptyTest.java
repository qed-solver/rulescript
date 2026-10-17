package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.FoldNotInEmpty;
import org.qed.RelRN;

public final class FoldNotInEmptyTest {
    public static void runTest() {
        var rule = new FoldNotInEmpty();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var left = RelRN.scan("HandLeft", "HandLeftType");
        var emptyRight = RelRN.scan("HandRight", "HandRightType").empty();
        var before = FaithfulPlans.of(left.join(JoinRelType.ANTI, "condition", emptyRight));
        var expected = FaithfulPlans.expected(left);
        RuleChecks.rewrites(rule, before, expected);

        var populatedRight = RelRN.scan("PopulatedRight", "PopulatedRightType");
        RuleChecks.doesNotApply(rule,
                FaithfulPlans.of(left.join(JoinRelType.ANTI, "condition", populatedRight)));
        RuleChecks.doesNotApply(rule,
                FaithfulPlans.of(left.join(JoinRelType.SEMI, "condition", emptyRight)));
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNotInEmpty test...");
        runTest();
    }
}

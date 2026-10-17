package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.FoldInEmpty;
import org.qed.RelRN;

public final class FoldInEmptyTest {
    public static void runTest() {
        var rule = new FoldInEmpty();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var left = RelRN.scan("HandLeft", "HandLeftType");
        var emptyRight = RelRN.scan("HandRight", "HandRightType").empty();
        var before = FaithfulPlans.of(left.join(JoinRelType.SEMI, "condition", emptyRight));
        var expected = FaithfulPlans.expected(left.empty());
        RuleChecks.rewrites(rule, before, expected);

        var populatedRight = RelRN.scan("PopulatedRight", "PopulatedRightType");
        RuleChecks.doesNotApply(rule,
                FaithfulPlans.of(left.join(JoinRelType.SEMI, "condition", populatedRight)));
    }

    public static void main(String[] args) {
        System.out.println("Running FoldInEmpty test...");
        runTest();
    }
}

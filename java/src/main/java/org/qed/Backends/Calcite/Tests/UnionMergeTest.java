package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.UnionMerge;

import java.util.List;

public class UnionMergeTest {
    public static void runTest() {
        var rule = new UnionMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var a = FaithfulPlans.of(org.qed.RelRN.scan("HandA", "HandType"));
        var b = FaithfulPlans.of(org.qed.RelRN.scan("HandB", "HandType"));
        var c = FaithfulPlans.of(org.qed.RelRN.scan("HandC", "HandType"));
        var d = FaithfulPlans.of(org.qed.RelRN.scan("HandD", "HandType"));
        RelNode nested = LogicalUnion.create(List.of(a, LogicalUnion.create(List.of(b, c), true), d), true);
        RelNode expected = LogicalUnion.create(List.of(a, b, c, d), true);
        RuleChecks.rewrites(rule, nested, expected);

        RelNode distinct = LogicalUnion.create(List.of(a, LogicalUnion.create(List.of(b, c), false), d), true);
        RuleChecks.doesNotApply(rule, distinct);
    }

    public static void main(String[] args) {
        System.out.println("Running UnionMerge test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("UnionMerge test failed: " + CalciteTester.failures() + " failure(s)");
        }
    }
}

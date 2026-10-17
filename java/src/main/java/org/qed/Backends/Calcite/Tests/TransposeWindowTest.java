package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.TransposeWindow;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public class TransposeWindowTest {
    public static void runTest() {
        var rule = new TransposeWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        RuleChecks.rewrites(rule, FaithfulPlans.of(rule.before()), FaithfulPlans.expected(rule.after()));

        var unrelated = RuleBuilder.create();
        var t = RuleChecks.table(unrelated, 3, false, false);
        var scan = unrelated.scan(t).build();
        RuleChecks.doesNotApply(rule, scan);
    }

    public static void main(String[] args) {
        System.out.println("Running TransposeWindow test...");
        runTest();
        if (CalciteTester.failures() != 0) throw new AssertionError("Calcite test failures: " + CalciteTester.failures());
    }
}

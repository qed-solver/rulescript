package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RuleBuilder;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.UnnestDecorrelate;

public final class UnnestDecorrelateTest {
    public static void runTest() {
        var rule = new UnnestDecorrelate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var before = FaithfulPlans.of(rule.before());
        var expected = FaithfulPlans.expected(rule.after());
        RuleChecks.rewrites(rule, before, expected);

        RuleChecks.doesNotApply(rule, leftOuterNearMiss());
    }

    private static RelNode leftOuterNearMiss() {
        var catalog = RuleBuilder.create();
        var left = RuleChecks.table(catalog, 2, false);
        var elements = RuleChecks.table(catalog, 1, false);
        RelBuilder b = RuleChecks.ruleBuilder(catalog);
        var leftNode = b.scan(left).build();
        var elementNode = b.scan(elements).build();
        b.push(leftNode).push(elementNode).join(JoinRelType.LEFT, b.literal(true));
        var joined = b.build();
        return b.push(joined).project(java.util.List.of(b.field(2))).build();
    }

    public static void main(String[] args) {
        System.out.println("Running UnnestDecorrelate test...");
        runTest();
    }
}

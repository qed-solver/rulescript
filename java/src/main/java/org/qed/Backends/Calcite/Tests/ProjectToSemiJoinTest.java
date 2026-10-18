package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.ProjectToSemiJoin;
import org.qed.RuleBuilder;

public final class ProjectToSemiJoinTest {
    public static void runTest() {
        var rule = new ProjectToSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var source = RuleBuilder.create();
        var left = RuleChecks.table(source, 2, false);
        var right = RuleChecks.table(source, 2, false);
        var b = RuleChecks.ruleBuilder(source);
        b.scan(left).scan(right).aggregate(b.groupKey(1));
        b.join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        b.project(b.field(1), b.field(0));
        var before = b.build();

        var expectedSource = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedSource, 2, false);
        var expectedRight = RuleChecks.table(expectedSource, 2, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedSource);
        expectedBuilder.scan(expectedLeft).scan(expectedRight);
        expectedBuilder.join(JoinRelType.SEMI,
                RuleChecks.eq(expectedBuilder, expectedBuilder.field(2, 0, 0), expectedBuilder.field(2, 1, 1)));
        expectedBuilder.project(expectedBuilder.field(1), expectedBuilder.field(0));
        var after = expectedBuilder.build();
        RuleChecks.rewrites(rule, before, after);

        var missSource = RuleBuilder.create();
        var missLeft = RuleChecks.table(missSource, 2, false);
        var missRight = RuleChecks.table(missSource, 2, false);
        var missBuilder = RuleChecks.ruleBuilder(missSource);
        missBuilder.scan(missLeft).scan(missRight).aggregate(missBuilder.groupKey(0));
        missBuilder.join(JoinRelType.INNER,
                RuleChecks.eq(missBuilder, missBuilder.field(2, 0, 0), missBuilder.field(2, 1, 0)));
        missBuilder.project(missBuilder.field(1), missBuilder.field(0));
        RuleChecks.doesNotApply(rule, missBuilder.build());
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}

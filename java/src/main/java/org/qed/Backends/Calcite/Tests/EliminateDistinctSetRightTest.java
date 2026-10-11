package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateDistinctSetRight;
import org.qed.RuleBuilder;

public class EliminateDistinctSetRightTest {

    public static void runTest() {
        var rule = new EliminateDistinctSetRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        emptyLeft(rule);
    }

    private static void emptyLeft(EliminateDistinctSetRight rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 3, false);
        var right = RuleChecks.table(builder, 3, false);
        var emptyLeft = builder.scan(left)
                .project(builder.field(2), builder.field(0), builder.field(1))
                .empty()
                .build();
        var before = builder.scan(right)
                .project(builder.field(2), builder.field(0), builder.field(1))
                .push(emptyLeft)
                .union(false, 2)
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(right)
                .project(plain.field(2), plain.field(0), plain.field(1))
                .aggregate(plain.groupKey(0, 1, 2))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var unionAll = builder.scan(right)
                .project(builder.field(2), builder.field(0), builder.field(1))
                .push(emptyLeft)
                .union(true, 2)
                .build();
        RuleChecks.doesNotApply(rule, unionAll);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateDistinctSetRight test...");
        runTest();
    }
}

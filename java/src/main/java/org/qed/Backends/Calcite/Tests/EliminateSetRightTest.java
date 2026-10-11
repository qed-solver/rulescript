package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateSetRight;
import org.qed.RuleBuilder;

public class EliminateSetRightTest {

    public static void runTest() {
        var rule = new EliminateSetRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        emptyLeft(rule);
    }

    private static void emptyLeft(EliminateSetRight rule) {
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
                .union(true, 2)
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(right)
                .project(plain.field(2), plain.field(0), plain.field(1))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nonEmptyLeft = builder.scan(left)
                .project(builder.field(2), builder.field(0), builder.field(1))
                .build();
        var unionWithRows = builder.scan(right)
                .project(builder.field(2), builder.field(0), builder.field(1))
                .push(nonEmptyLeft)
                .union(true, 2)
                .build();
        RuleChecks.doesNotApply(rule, unionWithRows);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateSetRight test...");
        runTest();
    }
}

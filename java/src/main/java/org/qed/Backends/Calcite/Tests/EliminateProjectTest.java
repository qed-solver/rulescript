package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateProject;
import org.qed.RuleBuilder;

import java.util.List;

public class EliminateProjectTest {

    public static void runTest() {
        var rule = new EliminateProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateProject rule) {
        var builder = RuleBuilder.create();
        var two = RuleChecks.table(builder, 2, false);
        var identity = builder.scan(two)
                .project(List.of(builder.field(0), builder.field(1)), List.of(), true).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(two).build();
        RuleChecks.rewrites(rule, identity, after);

        var swapped = builder.scan(two)
                .project(List.of(builder.field(1), builder.field(0)), List.of(), true).build();
        RuleChecks.doesNotApply(rule, swapped);
        var narrow = builder.scan(two)
                .project(List.of(builder.field(0)), List.of(), true).build();
        RuleChecks.doesNotApply(rule, narrow);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateProject test...");
        runTest();
    }
}

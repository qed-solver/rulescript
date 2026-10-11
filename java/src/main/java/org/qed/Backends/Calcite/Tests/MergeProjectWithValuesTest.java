package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.MergeProjectWithValues;
import org.qed.RuleBuilder;

public class MergeProjectWithValuesTest {

    public static void runTest() {
        var rule = new MergeProjectWithValues();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        singleRowValues(rule);
    }

    private static void singleRowValues(MergeProjectWithValues rule) {
        var builder = RuleBuilder.create();
        var before = builder.values(new String[]{"c0", "c1"}, 1, 2)
                .project(builder.field(0), builder.literal(3))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.values(new String[]{"c0", "c1"}, 1, 3).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, builder.values(new String[]{"c0", "c1"}, 1, 2)
                .project(builder.field(1), builder.literal(3))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running MergeProjectWithValues test...");
        runTest();
    }
}

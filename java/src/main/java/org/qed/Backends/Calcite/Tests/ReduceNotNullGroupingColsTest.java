package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.ReduceNotNullGroupingCols;
import org.qed.RuleBuilder;

public class ReduceNotNullGroupingColsTest {

    public static void runTest() {
        var rule = new ReduceNotNullGroupingCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ReduceNotNullGroupingCols rule) {
        var builder = RuleBuilder.create();
        var notNull = RuleChecks.table(builder, 1, false, false);
        var twoColumns = RuleChecks.table(builder, 2, false, false);
        var before = builder.scan(notNull)
                .project(builder.field(0), builder.field(0))
                .aggregate(builder.groupKey(0, 1))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(notNull)
                .project(plain.field(0), plain.field(0))
                .aggregate(plain.groupKey(0))
                .project(plain.field(0), plain.field(0))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var distinctColumns = builder.scan(twoColumns)
                .project(builder.field(0), builder.field(1))
                .aggregate(builder.groupKey(0, 1))
                .build();
        RuleChecks.doesNotApply(rule, distinctColumns);
    }

    public static void main(String[] args) {
        System.out.println("Running ReduceNotNullGroupingCols test...");
        runTest();
    }
}

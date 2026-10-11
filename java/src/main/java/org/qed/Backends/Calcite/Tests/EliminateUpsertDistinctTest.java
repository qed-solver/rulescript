package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.EliminateUpsertDistinct;
import org.qed.RuleBuilder;

import java.util.List;

public class EliminateUpsertDistinctTest {

    public static void runTest() {
        var rule = new EliminateUpsertDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateUpsertDistinct rule) {
        var builder = RuleBuilder.create();
        var keyed = RuleChecks.table(builder, 1, true, true);
        var plain = RuleChecks.table(builder, 1, false, true);
        var before = LogicalAggregate.create(builder.scan(keyed).build(), ImmutableBitSet.of(0), null, List.of());
        var rewritten = RuleChecks.ruleBuilder(builder);
        var after = rewritten.scan(keyed)
                .project(rewritten.field(0))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nonKey = builder.scan(plain)
                .aggregate(builder.groupKey(0))
                .build();
        RuleChecks.doesNotApply(rule, nonKey);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateUpsertDistinct test...");
        runTest();
    }
}

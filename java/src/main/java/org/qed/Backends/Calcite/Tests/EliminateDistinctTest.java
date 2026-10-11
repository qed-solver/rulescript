package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.EliminateDistinct;
import org.qed.RuleBuilder;

import java.util.List;

public class EliminateDistinctTest {

    public static void runTest() {
        var rule = new EliminateDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateDistinct rule) {
        var builder = RuleBuilder.create();
        var keyed = RuleChecks.table(builder, 1, true, false);
        var plain = RuleChecks.table(builder, 1, false, false);
        var nullableKeyed = RuleChecks.table(builder, 1, true, true);
        var b = RuleChecks.ruleBuilder(builder);

        var distinctOverKey = LogicalAggregate.create(builder.scan(keyed).build(),
                List.of(), ImmutableBitSet.of(0), null, List.of());
        var projectedKey = b.scan(keyed).project(b.field(0)).build();
        RuleChecks.rewrites(rule, distinctOverKey, projectedKey);

        var distinctOverColumn = LogicalAggregate.create(builder.scan(plain).build(),
                List.of(), ImmutableBitSet.of(0), null, List.of());
        RuleChecks.doesNotApply(rule, distinctOverColumn);

        var distinctOverNullableKey = LogicalAggregate.create(builder.scan(nullableKeyed).build(),
                List.of(), ImmutableBitSet.of(0), null, List.of());
        RuleChecks.doesNotApply(rule, distinctOverNullableKey);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateDistinct test...");
        runTest();
    }
}

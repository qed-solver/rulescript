package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.ReplaceDistinctWithAggregate;
import org.qed.RuleBuilder;

import java.util.List;

public class ReplaceDistinctWithAggregateTest {
    public static void runTest() {
        var rule = new ReplaceDistinctWithAggregate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltCase(rule);
    }

    private static void handBuiltCase(ReplaceDistinctWithAggregate rule) {
        var schema = RuleBuilder.create();
        var table = RuleChecks.table(schema, 2, false, false);
        var b = RuleChecks.ruleBuilder(schema);

        RelNode left = b.scan(table).build();
        RelNode right = b.scan(table).build();
        RelNode before = LogicalIntersect.create(List.of(left, right), false);

        RelNode expectedInput = b.scan(table).build();
        RelNode expected = LogicalAggregate.create(expectedInput, List.of(), ImmutableBitSet.of(0, 1), null,
                List.of());
        RuleChecks.rewrites(rule, before, expected);

        RelNode allIntersect = LogicalIntersect.create(List.of(left, right), true);
        RuleChecks.doesNotApply(rule, allIntersect);
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceDistinctWithAggregate test...");
        runTest();
    }
}

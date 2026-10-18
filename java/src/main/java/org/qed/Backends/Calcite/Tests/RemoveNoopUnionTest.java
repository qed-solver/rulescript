package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RuleBuilder;

public class RemoveNoopUnionTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.RemoveNoopUnion();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.qed.RRule rule) {
        var catalog = RuleBuilder.create();
        var table = RuleChecks.table(catalog, 1, false, true);
        var b = RuleChecks.ruleBuilder(catalog);
        RelNode first = b.scan(table).build();
        RelNode second = b.scan(table).build();
        RelNode union = LogicalUnion.create(java.util.List.of(first, second), true);
        RelNode before = LogicalAggregate.create(union, java.util.List.of(), ImmutableBitSet.of(0),
                java.util.List.of(ImmutableBitSet.of(0)), java.util.List.of());

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false, true);
        var eb = RuleChecks.ruleBuilder(expectedBuilder);
        RelNode expectedInput = eb.scan(expectedTable).build();
        RelNode expected = LogicalAggregate.create(expectedInput, java.util.List.of(), ImmutableBitSet.of(0),
                java.util.List.of(ImmutableBitSet.of(0)), java.util.List.of());
        RuleChecks.rewrites(rule, before, expected);

        var missCatalog = RuleBuilder.create();
        var leftName = RuleChecks.table(missCatalog, 1, false, true);
        var rightName = RuleChecks.table(missCatalog, 1, false, true);
        var mb = RuleChecks.ruleBuilder(missCatalog);
        RelNode left = mb.scan(leftName).build();
        RelNode right = mb.scan(rightName).build();
        RelNode distinctUnion = LogicalUnion.create(java.util.List.of(left, right), true);
        RelNode nearMiss = LogicalAggregate.create(distinctUnion, java.util.List.of(), ImmutableBitSet.of(0),
                java.util.List.of(ImmutableBitSet.of(0)), java.util.List.of());
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveNoopUnion test...");
        runTest();
    }
}

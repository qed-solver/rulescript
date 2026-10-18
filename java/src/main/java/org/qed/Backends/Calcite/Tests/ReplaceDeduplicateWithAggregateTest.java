package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ReplaceDeduplicateWithAggregate;

import java.util.List;

public class ReplaceDeduplicateWithAggregateTest {
    public static void runTest() {
        var rule = new ReplaceDeduplicateWithAggregate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        rewritesDistinctFullRowsToGrouping();
        rejectsAllRowsIntersection();
    }

    private static void rewritesDistinctFullRowsToGrouping() {
        var builder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, true);
        var source = builder.scan(table).build();
        var duplicateInput = builder.scan(table).build();
        RelNode before = LogicalIntersect.create(List.of(source, duplicateInput), false);

        var expectedBuilder = org.qed.RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 2, false, true);
        var expectedInput = expectedBuilder.scan(expectedTable).build();
        RelNode expected = LogicalAggregate.create(expectedInput, List.of(), ImmutableBitSet.of(0, 1), null,
                List.of());

        RuleChecks.rewrites(new ReplaceDeduplicateWithAggregate(), before, expected);
    }

    private static void rejectsAllRowsIntersection() {
        var builder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, true);
        var left = builder.scan(table).build();
        var right = builder.scan(table).build();
        RelNode allRows = LogicalIntersect.create(List.of(left, right), true);
        RuleChecks.doesNotApply(new ReplaceDeduplicateWithAggregate(), allRows);
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceDeduplicateWithAggregate test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateMerge;

public class AggregateMergeTest {
    public static void runTest() {
        var rule = new AggregateMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var scan = b.scan(table).build();
        var inputRef = new RexInputRef(0, scan.getRowType().getFieldList().get(0).getType());
        var source = LogicalProject.create(scan, java.util.List.of(),
                java.util.List.of(inputRef, inputRef, inputRef), java.util.List.of("key1", "key2", "value"));
        var bottomGroupSet = ImmutableBitSet.of(0);
        var aggregateCall = AggregateCall.create(builder.genericAggregateOp("aggregateValue",
                        org.qed.RelType.fromString("INTEGER", true)), false, false, false,
                java.util.List.of(2), -1, null, RelCollations.EMPTY, 2, source, null, "aggregateValue");
        var bottom = LogicalAggregate.create(source, java.util.List.of(), ImmutableBitSet.of(0, 1),
                null, java.util.List.of(aggregateCall));
        var top = LogicalAggregate.create(bottom, java.util.List.of(), ImmutableBitSet.of(0),
                null, java.util.List.of());
        var merged = LogicalAggregate.create(scan, java.util.List.of(), bottomGroupSet, null,
                java.util.List.of());
        var mergedValue = new RexInputRef(0, merged.getRowType().getFieldList().get(0).getType());
        var expected = LogicalProject.create(merged, java.util.List.of(),
                java.util.List.of(b.getRexBuilder().makeCast(scan.getRowType().getFieldList().get(0).getType(),
                        mergedValue)), java.util.List.of("key1"));
        RuleChecks.rewrites(rule, top, expected);

        var nearMiss = LogicalAggregate.create(bottom, java.util.List.of(), ImmutableBitSet.of(1),
                null, java.util.List.of());
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateMerge test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError(CalciteTester.failures() + " test failures");
        }
    }
}

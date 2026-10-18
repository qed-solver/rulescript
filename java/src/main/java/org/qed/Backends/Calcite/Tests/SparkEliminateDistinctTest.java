package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.SparkEliminateDistinct;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

import java.util.List;

public class SparkEliminateDistinctTest {
    public static void runTest() {
        var rule = new SparkEliminateDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        rewritesHandBuiltPlan();
        rejectsNonUniqueScan();
    }

    private static void rewritesHandBuiltPlan() {
        var rule = new SparkEliminateDistinct();
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, true, false);
        var scan = builder.scan(table).build();
        var input = LogicalProject.create(scan, List.of(), List.of(builder.push(scan).field(0)), List.of("key"));
        var groupSet = ImmutableBitSet.of(0);
        var distinct = AggregateCall.create(org.qed.RuleBuilder.create()
                        .genericAggregateOp("f", org.qed.RelType.fromString("INTEGER", true)),
                true, false, false, List.of(0), -1, null, RelCollations.EMPTY, 1, input, null, "f");
        RelNode before = LogicalAggregate.create(input, List.of(), groupSet, null, List.of(distinct));
        var plain = AggregateCall.create(distinct.getAggregation(), false, false, false, List.of(0), -1, null,
                RelCollations.EMPTY, 1, scan, null, "f");
        RelNode after = LogicalAggregate.create(scan, List.of(), groupSet, null, List.of(plain));
        RuleChecks.rewrites(rule, before, after);
    }

    private static void rejectsNonUniqueScan() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var scan = builder.scan(table).build();
        var input = builder.push(scan).build();
        var groupSet = ImmutableBitSet.of(0);
        var distinct = AggregateCall.create(org.qed.RuleBuilder.create()
                        .genericAggregateOp("f", org.qed.RelType.fromString("INTEGER", true)),
                true, false, false, List.of(0), -1, null, RelCollations.EMPTY, 1, input, null, "f");
        var plan = LogicalAggregate.create(input, List.of(), groupSet, null, List.of(distinct));
        RuleChecks.doesNotApply(new SparkEliminateDistinct(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running SparkEliminateDistinct test...");
        runTest();
    }
}

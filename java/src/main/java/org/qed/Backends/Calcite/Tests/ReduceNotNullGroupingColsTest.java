package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RuleBuilder;

import java.util.List;

public class ReduceNotNullGroupingColsTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.ReduceNotNullGroupingCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.qed.RRuleInstances.ReduceNotNullGroupingCols rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var source = builder.scan(table).build();
        var sourceField = source.getCluster().getRexBuilder().makeInputRef(source, 0);
        var project = LogicalProject.create(source, List.of(),
                List.<RexNode>of(sourceField, sourceField), List.of("g", "r"));
        var plan = LogicalAggregate.create(project, List.of(), ImmutableBitSet.of(0, 1), null, List.of());

        var reduced = LogicalAggregate.create(project, List.of(), ImmutableBitSet.of(0), null, List.of());
        var reducedField = reduced.getCluster().getRexBuilder().makeInputRef(reduced, 0);
        var expected = LogicalProject.create(reduced, List.of(),
                List.<RexNode>of(reducedField, reducedField), List.of("g", "r"));
        RuleChecks.rewrites(rule, plan, expected);

        var nullableBuilder = RuleBuilder.create();
        var nullableTable = RuleChecks.table(nullableBuilder, 1, false, true);
        var nullableSource = nullableBuilder.scan(nullableTable).build();
        var nullableField = nullableSource.getCluster().getRexBuilder().makeInputRef(nullableSource, 0);
        var nullableProject = LogicalProject.create(nullableSource, List.of(),
                List.<RexNode>of(nullableField, nullableField), List.of("g", "r"));
        var nullablePlan = LogicalAggregate.create(nullableProject, List.of(), ImmutableBitSet.of(0, 1), null,
                List.<AggregateCall>of());
        RuleChecks.doesNotApply(rule, nullablePlan);
    }

    public static void main(String[] args) {
        System.out.println("Running ReduceNotNullGroupingCols test...");
        runTest();
    }
}

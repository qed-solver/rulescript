package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.RemoveRedundantAggregates;
import org.qed.RuleBuilder;

import java.util.List;

public final class RemoveRedundantAggregatesTest {
    private RemoveRedundantAggregatesTest() {}

    public static void runTest() {
        var rule = new RemoveRedundantAggregates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RemoveRedundantAggregates rule) {
        var schema = new java.util.ArrayList<kala.tuple.Tuple2<org.qed.RelType, Boolean>>();
        schema.add(kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false));
        var table = RuleBuilder.create().createQedTable(kala.collection.Seq.from(schema));
        var catalog = RuleBuilder.create();
        catalog.addTable(table);
        RelBuilder b = catalog.transform(CalciteRuntime::configure);
        b.scan(table.getName());
        RelNode scan = b.build();
        RelNode inputProjection = LogicalProject.create(scan, List.of(),
                List.of(b.getRexBuilder().makeInputRef(scan, 0), b.getRexBuilder().makeInputRef(scan, 0),
                        b.getRexBuilder().makeInputRef(scan, 0)), List.of("k1", "k2", "v"));
        var groupSet = ImmutableBitSet.of(0, 1);
        var lowerCall = AggregateCall.create(org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT,
                false, false, false, List.of(2), -1, null, RelCollations.EMPTY, 2, inputProjection, null, "unused");
        RelNode lower = LogicalAggregate.create(inputProjection, List.of(), groupSet, null, List.of(lowerCall));
        RelNode before = LogicalAggregate.create(lower, List.of(), ImmutableBitSet.of(0), null, List.of());

        // Build the expected result from an independent scan and the retained key projection.
        b = catalog.transform(CalciteRuntime::configure);
        b.scan(table.getName());
        b.project(List.of(b.field(0)));
        RelNode projected = b.build();
        RelNode after = LogicalAggregate.create(projected, List.of(), ImmutableBitSet.of(0), null, List.of());
        RuleChecks.rewrites(rule, before, after);

        // A multi-column scan breaks the explicit one-column input scope.
        var wideSchema = kala.collection.Seq.of(
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false),
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false));
        var wideTable = RuleBuilder.create().createQedTable(wideSchema);
        catalog.addTable(wideTable);
        b = catalog.transform(CalciteRuntime::configure);
        b.scan(wideTable.getName());
        scan = b.build();
        inputProjection = LogicalProject.create(scan, List.of(),
                List.of(b.getRexBuilder().makeInputRef(scan, 0), b.getRexBuilder().makeInputRef(scan, 1),
                        b.getRexBuilder().makeInputRef(scan, 0)), List.of("k1", "k2", "v"));
        lowerCall = AggregateCall.create(org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT,
                false, false, false, List.of(2), -1, null, RelCollations.EMPTY, 2, inputProjection, null, "unused");
        lower = LogicalAggregate.create(inputProjection, List.of(), groupSet, null, List.of(lowerCall));
        before = LogicalAggregate.create(lower, List.of(), ImmutableBitSet.of(0), null, List.of());
        RuleChecks.doesNotApply(rule, before);

        // Referencing both lower group keys violates the requirement that key 2 be unreferenced.
        before = LogicalAggregate.create(lower, List.of(), ImmutableBitSet.of(0, 1), null, List.of());
        RuleChecks.doesNotApply(rule, before);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveRedundantAggregates test...");
        runTest();
    }
}

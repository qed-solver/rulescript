package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateUnionAggregateFirstTest {
    static RelNode distinct(RelNode input) {
        var keys = ImmutableBitSet.range(input.getRowType().getFieldCount());
        return LogicalAggregate.create(input, List.of(), keys, List.of(keys), List.of());
    }

    public static void runTest() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var left = builder.scan(table.getName()).build();
        var right = builder.scan(table.getName()).build();
        var before = distinct(LogicalUnion.create(List.of(distinct(left), right), true));
        var after = distinct(LogicalUnion.create(List.of(left, right), true));

        new CalciteTester().verify(CalciteTester.loadRule(
                org.qed.Backends.Calcite.Generated.AggregateUnionAggregateFirst.Config.DEFAULT.toRule()),
                before, after);
    }
}

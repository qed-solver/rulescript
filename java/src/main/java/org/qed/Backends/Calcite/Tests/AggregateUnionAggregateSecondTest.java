package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.rel.RelNode;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateUnionAggregateSecondTest {
    public static void runTest() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var left = builder.scan(table.getName()).build();
        var right = builder.scan(table.getName()).build();
        java.util.function.Function<RelNode, RelNode> distinct =
                AggregateUnionAggregateFirstTest::distinct;
        var before = distinct.apply(LogicalUnion.create(List.of(left, distinct.apply(right)), true));
        var after = distinct.apply(LogicalUnion.create(List.of(left, right), true));

        new CalciteTester().verify(CalciteTester.loadRule(
                org.qed.Backends.Calcite.Generated.AggregateUnionAggregateSecond.Config.DEFAULT.toRule()),
                before, after);
    }
}

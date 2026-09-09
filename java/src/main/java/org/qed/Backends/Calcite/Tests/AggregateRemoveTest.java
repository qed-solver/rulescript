package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class AggregateRemoveTest {
    public static void runTest() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), true)));
        builder.addTable(table);
        var source = builder.scan(table.getName()).build();
        var before = AggregateUnionAggregateFirstTest.distinct(source);

        new CalciteTester().verify(CalciteTester.loadRule(
                org.qed.Backends.Calcite.Generated.AggregateRemove.Config.DEFAULT.toRule()),
                before, source);
    }
}

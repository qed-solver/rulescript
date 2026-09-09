package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class SetOpAllMergeTest {
    public static void runTest() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var a = builder.scan(table.getName()).build();
        var b = builder.scan(table.getName()).build();
        var c = builder.scan(table.getName()).build();

        var innerUnion = builder.push(a).push(b).union(true, 2).build();
        var unionBefore = builder.push(innerUnion).push(c).union(true, 2).build();
        var unionAfter = builder.push(a).push(b).push(c).union(true, 3).build();
        new CalciteTester().verify(
                CalciteTester.loadRule(org.qed.Backends.Calcite.Generated.UnionAllMerge.Config.DEFAULT.toRule()),
                unionBefore, unionAfter);

    }
}

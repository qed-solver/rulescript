package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneEmptySetOpAdditionalTest {
    public static void runTest() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var source = builder.scan(table.getName()).build();
        var empty = builder.push(source).empty().build();

        new CalciteTester().verify(
                CalciteTester.loadRule(
                        org.qed.Backends.Calcite.Generated.PruneEmptyUnionAllLeft.Config.DEFAULT.toRule()),
                LogicalUnion.create(List.of(empty, source), true), source);
        new CalciteTester().verify(
                CalciteTester.loadRule(
                        org.qed.Backends.Calcite.Generated.PruneEmptyUnionAllRight.Config.DEFAULT.toRule()),
                LogicalUnion.create(List.of(source, empty), true), source);
        new CalciteTester().verify(
                CalciteTester.loadRule(
                        org.qed.Backends.Calcite.Generated.PruneEmptyIntersectLeft.Config.DEFAULT.toRule()),
                LogicalIntersect.create(List.of(empty, source), false), empty);
    }
}

package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class SetOpEliminatorTest {
    public static void runTest() {
        var tester = new CalciteTester();
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var source = builder.scan(table.getName()).build();

        tester.verify(
                CalciteTester.loadRule(
                        org.qed.Backends.Calcite.Generated.UnionEliminator.Config.DEFAULT.toRule()),
                LogicalUnion.create(List.of(source), true), source);
    }
}

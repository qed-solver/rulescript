package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectRemoveTest {
    public static void runTest() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false),
                Tuple.of(RelType.fromString("VARCHAR", true), false)));
        builder.addTable(table);
        var source = builder.scan(table.getName()).build();
        var rexBuilder = source.getCluster().getRexBuilder();
        var projects = source.getRowType().getFieldList().stream()
                .map(field -> rexBuilder.makeInputRef(field.getType(), field.getIndex()))
                .toList();
        var before = org.apache.calcite.rel.logical.LogicalProject.create(
                source, List.of(), projects, source.getRowType());

        new CalciteTester().verify(
                CalciteTester.loadRule(org.qed.Backends.Calcite.Generated.ProjectRemove.Config.DEFAULT.toRule()),
                before, source);
    }
}

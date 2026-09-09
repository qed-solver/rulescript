package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class SetOpTransposeTest {
    public static void runTest() {
        testProjectUnionAll();
        testFilterUnionAll();
        testFilterIntersect();
        testFilterMinus();
    }

    private record Fixture(RuleBuilder builder, String tableName) {}

    private static Fixture fixture() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        return new Fixture(builder, table.getName());
    }

    private static void testProjectUnionAll() {
        var fixture = fixture();
        var builder = fixture.builder();
        var left = builder.scan(fixture.tableName()).build();
        var right = builder.scan(fixture.tableName()).build();
        var projection = builder.genericProjectionOp(
                "projection", RelType.fromString("INTEGER", true));
        var union = builder.push(left).push(right).union(true, 2).build();
        var before = builder.push(union).project(builder.call(projection, builder.fields())).build();
        var leftProject = builder.push(left).project(builder.call(projection, builder.fields())).build();
        var rightProject = builder.push(right).project(builder.call(projection, builder.fields())).build();
        var after = builder.push(leftProject).push(rightProject).union(true, 2).build();
        new CalciteTester().verify(
                CalciteTester.loadRule(org.qed.Backends.Calcite.Generated.ProjectUnionAllTranspose.Config.DEFAULT.toRule()),
                before, after);
    }

    private static void testFilterUnionAll() {
        testFilter("union", true);
    }

    private static void testFilterIntersect() {
        testFilter("intersect", false);
    }

    private static void testFilterMinus() {
        testFilter("minus", false);
    }

    private static void testFilter(String operation, boolean all) {
        var fixture = fixture();
        var builder = fixture.builder();
        var left = builder.scan(fixture.tableName()).build();
        var right = builder.scan(fixture.tableName()).build();
        var predicate = builder.genericPredicateOp("condition", true);
        builder.push(left).push(right);
        if (operation.equals("union")) {
            builder.union(all, 2);
        } else if (operation.equals("intersect")) {
            builder.intersect(all, 2);
        } else {
            builder.minus(all, 2);
        }
        var setOp = builder.build();
        var before = builder.push(setOp).filter(builder.call(predicate, builder.fields())).build();
        var leftFilter = builder.push(left).filter(builder.call(predicate, builder.fields())).build();
        var rightFilter = builder.push(right).filter(builder.call(predicate, builder.fields())).build();
        builder.push(leftFilter).push(rightFilter);
        if (operation.equals("union")) {
            builder.union(all, 2);
        } else if (operation.equals("intersect")) {
            builder.intersect(all, 2);
        } else {
            builder.minus(all, 2);
        }
        var after = builder.build();

        var rule = switch (operation) {
            case "union" -> org.qed.Backends.Calcite.Generated.FilterUnionAllTranspose.Config.DEFAULT.toRule();
            case "intersect" -> org.qed.Backends.Calcite.Generated.FilterIntersectTranspose.Config.DEFAULT.toRule();
            default -> org.qed.Backends.Calcite.Generated.FilterMinusTranspose.Config.DEFAULT.toRule();
        };
        new CalciteTester().verify(CalciteTester.loadRule(rule), before, after);
    }
}

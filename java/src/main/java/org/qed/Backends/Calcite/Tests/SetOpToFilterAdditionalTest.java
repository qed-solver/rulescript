package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.apache.calcite.rel.logical.LogicalMinus;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class SetOpToFilterAdditionalTest {
    private static void verify(boolean union) {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var source = builder.scan(table.getName()).build();
        var rexBuilder = source.getCluster().getRexBuilder();
        var field = rexBuilder.makeInputRef(source, 0);
        var leftCondition = rexBuilder.makeCall(
                builder.genericPredicateOp("leftCondition", true), field);
        var rightCondition = rexBuilder.makeCall(
                builder.genericPredicateOp("rightCondition", true), field);
        var left = LogicalFilter.create(source, leftCondition);
        var right = LogicalFilter.create(source, rightCondition);
        RelNode before = union
                ? LogicalUnion.create(List.of(left, right), false)
                : LogicalIntersect.create(List.of(left, right), false);
        var combined = union
                ? rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.OR,
                    leftCondition, rightCondition)
                : rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                    leftCondition, rightCondition);
        var after = AggregateUnionAggregateFirstTest.distinct(
                LogicalFilter.create(source, combined));
        var name = union ? "UnionFilterToFilter" : "IntersectFilterToFilter";
        new CalciteTester().verify(
                CalciteTester.loadRule(CalciteTester.generatedRule(name)), before, after);
    }

    private static void verifyMinus() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(
                Tuple.of(RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        var source = builder.scan(table.getName()).build();
        var rexBuilder = source.getCluster().getRexBuilder();
        var field = rexBuilder.makeInputRef(source, 0);
        var leftCondition = rexBuilder.makeCall(
                builder.genericPredicateOp("leftCondition", true), field);
        var rightCondition = rexBuilder.makeCall(
                builder.genericPredicateOp("rightCondition", true), field);
        var left = LogicalFilter.create(source, leftCondition);
        var right = LogicalFilter.create(source, rightCondition);
        var before = LogicalMinus.create(List.of(left, right), false);
        var isNotTrue = rexBuilder.makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.IS_NOT_TRUE, rightCondition);
        var combined = rexBuilder.makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                leftCondition, isNotTrue);
        var after = AggregateUnionAggregateFirstTest.distinct(
                LogicalFilter.create(source, combined));
        new CalciteTester().verify(CalciteTester.loadRule(
                CalciteTester.generatedRule("MinusFilterToFilter")), before, after);
    }

    public static void runTest() {
        verify(true);
        verify(false);
        verifyMinus();
    }
}

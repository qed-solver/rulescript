package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ExtractGroupingPythonUDFFromAggregate;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

import java.util.List;

public class ExtractGroupingPythonUDFFromAggregateTest {
    public static void runTest() {
        var rule = new ExtractGroupingPythonUDFFromAggregate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive();
        handBuiltNoMatch();
    }

    private static void handBuiltPositive() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var scan = builder.scan(table).build();
        var groupingUdf = RuleBuilder.create().genericProjectionOp("hand_group", new RelType.VarType("group_type", true));
        var concreteAggregate = RuleBuilder.create().genericAggregateOp("hand_sum", RelType.fromString("INTEGER", true));
        var rexBuilder = scan.getCluster().getRexBuilder();
        var udf = rexBuilder.makeCall(groupingUdf, rexBuilder.makeInputRef(scan, 0));
        var aggregate = (org.apache.calcite.sql.SqlAggFunction) concreteAggregate;
        var sourceRef = rexBuilder.makeInputRef(scan, 0);
        var projected = builder.transform(CalciteRuntime::configure).push(scan)
                .project(List.of(udf, sourceRef), List.of("group", "value"), true).build();
        var sourceAgg = org.apache.calcite.rel.core.AggregateCall.create(aggregate, false, false, false,
                List.of(1), -1, null, RelCollations.EMPTY, 1, projected, null, "hand_sum");
        var before = LogicalAggregate.create(projected, List.of(), ImmutableBitSet.of(0), null, List.of(sourceAgg));
        var expectedBuilder = RuleBuilder.create().transform(CalciteRuntime::configure).push(scan)
                .project(List.of(udf, sourceRef), List.of("group", "value"), true);
        var expectedCall = expectedBuilder.aggregateCall(aggregate, false, null, "hand_sum",
                List.of(expectedBuilder.field(1)));
        var after = expectedBuilder.aggregate(expectedBuilder.groupKey(expectedBuilder.field(0)), expectedCall)
                .build();
        RuleChecks.rewrites(rule(), before, after);
    }

    private static void handBuiltNoMatch() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var scan = builder.scan(table).build();
        var udf = RuleBuilder.create().genericProjectionOp("hand_group_miss", new RelType.VarType("group_type", true));
        var rexBuilder = scan.getCluster().getRexBuilder();
        rexBuilder.makeCall(udf, rexBuilder.makeInputRef(scan, 0));
        var aggregate = (org.apache.calcite.sql.SqlAggFunction) RuleBuilder.create()
                .genericAggregateOp("hand_count_miss", RelType.fromString("INTEGER", true));
        var call = org.apache.calcite.rel.core.AggregateCall.create(aggregate, false, false, false,
                List.of(), -1, null, RelCollations.EMPTY, 1, scan, null, "hand_count_miss");
        var noMatch = LogicalAggregate.create(scan, List.of(), ImmutableBitSet.of(0), null, List.of(call));
        RuleChecks.doesNotApply(rule(), noMatch);
    }

    private static ExtractGroupingPythonUDFFromAggregate rule() {
        return new ExtractGroupingPythonUDFFromAggregate();
    }

    public static void main(String[] args) {
        System.out.println("Running ExtractGroupingPythonUDFFromAggregate test...");
        runTest();
    }
}

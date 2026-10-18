package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldGroupingOperators;
import org.qed.RelRN;
import org.qed.RuleBuilder;

import java.util.List;

public class FoldGroupingOperatorsTest {
    public static void runTest() {
        var rule = new FoldGroupingOperators();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive();
        handBuiltNoMatch();
    }

    private static void handBuiltPositive() {
        var pair = builderAndScan();
        var b = pair.builder();
        var scan = pair.scan();
        var projected = b.push(scan).project(b.fields(), List.of(), true).build();
        var inner = aggregate(projected, ImmutableBitSet.of(0), List.of(countCall(b, projected)));
        var before = aggregate(inner, ImmutableBitSet.of(0), List.of());
        var expected = aggregate(scan, ImmutableBitSet.of(0), List.of());
        RuleChecks.rewrites(new FoldGroupingOperators(), before, expected);
    }

    private static void handBuiltNoMatch() {
        var pair = builderAndScan();
        var b = pair.builder();
        var scan = pair.scan();
        RuleChecks.doesNotApply(new FoldGroupingOperators(), scan);
    }

    private record BuilderAndScan(RelBuilder builder, RelNode scan) {}

    private static BuilderAndScan builderAndScan() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var calciteBuilder = RuleChecks.ruleBuilder(builder);
        return new BuilderAndScan(calciteBuilder, calciteBuilder.scan(table).build());
    }

    private static RelNode aggregate(RelNode input, ImmutableBitSet groups, List<org.apache.calcite.rel.core.AggregateCall> calls) {
        return LogicalAggregate.create(input, List.of(), groups, null, calls);
    }

    private static org.apache.calcite.rel.core.AggregateCall countCall(RelBuilder b, RelNode input) {
        return org.apache.calcite.rel.core.AggregateCall.create(org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT,
                false, false, false, List.of(0), -1, null, org.apache.calcite.rel.RelCollations.EMPTY,
                1, input, null, "v");
    }

    public static void main(String[] args) {
        System.out.println("Running FoldGroupingOperators test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.EliminateAggregateFilter;
import org.qed.RelType;
import org.qed.RuleBuilder;

public final class EliminateAggregateFilterTest {
    public static void runTest() {
        var rule = new EliminateAggregateFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        RuleChecks.rewrites(rule, concrete(true), unfilteredAggregate());
        RuleChecks.doesNotApply(rule, nontrivialFilter());
    }

    private static RelNode concrete(boolean alwaysTrue) {
        var qedBuilder = RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 2, false);
        var aggregate = (SqlAggFunction) qedBuilder.genericAggregateOp("f", RelType.fromString("INTEGER", true));
        var builder = qedBuilder.transform(CalciteRuntime::configure);
        builder.scan(table);
        var source = builder.build();
        var condition = alwaysTrue ? builder.getRexBuilder().makeLiteral(true)
                : builder.getRexBuilder().makeLiteral(false);
        var filtered = LogicalFilter.create(source, condition);
        var groupSet = ImmutableBitSet.of(0);
        var call = AggregateCall.create(aggregate, false, false, false, java.util.List.of(1), -1, null,
                RelCollations.EMPTY, groupSet.cardinality(), filtered, null, "f");
        return LogicalAggregate.create(filtered, java.util.List.of(), groupSet, null, java.util.List.of(call));
    }

    private static RelNode nontrivialFilter() {
        var qedBuilder = RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 2, false);
        var aggregate = (SqlAggFunction) qedBuilder.genericAggregateOp("f", RelType.fromString("INTEGER", true));
        var builder = qedBuilder.transform(CalciteRuntime::configure);
        builder.scan(table);
        var source = builder.build();
        var condition = RuleChecks.gt(builder, builder.getRexBuilder().makeInputRef(source, 1), 10);
        var filtered = LogicalFilter.create(source, condition);
        var groupSet = ImmutableBitSet.of(0);
        var call = AggregateCall.create(aggregate, false, false, false, java.util.List.of(1), -1, null,
                RelCollations.EMPTY, groupSet.cardinality(), filtered, null, "f");
        return LogicalAggregate.create(filtered, java.util.List.of(), groupSet, null, java.util.List.of(call));
    }

    private static RelNode unfilteredAggregate() {
        var qedBuilder = RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 2, false);
        var aggregate = (SqlAggFunction) qedBuilder.genericAggregateOp("f", RelType.fromString("INTEGER", true));
        var builder = qedBuilder.transform(CalciteRuntime::configure);
        builder.scan(table);
        var source = builder.build();
        var groupSet = ImmutableBitSet.of(0);
        var call = AggregateCall.create(aggregate, false, false, false, java.util.List.of(1), -1, null,
                RelCollations.EMPTY, groupSet.cardinality(), source, null, "f");
        return LogicalAggregate.create(source, java.util.List.of(), groupSet, null, java.util.List.of(call));
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAggregateFilter test...");
        runTest();
    }
}

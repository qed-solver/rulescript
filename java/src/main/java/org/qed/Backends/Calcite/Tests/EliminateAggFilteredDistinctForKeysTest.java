package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RelType;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.EliminateAggFilteredDistinctForKeys;

import java.util.List;

public class EliminateAggFilteredDistinctForKeysTest {
    public static void runTest() {
        var rule = new EliminateAggFilteredDistinctForKeys();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsFilteredAggregate();
    }

    private static void rewritesHandBuiltPlan() {
        var builder = RuleBuilder.create();
        var name = RuleChecks.table(builder, 1, true, false);
        var b = RuleChecks.ruleBuilder(builder);
        var input = b.scan(name).build();
        var groupSet = ImmutableBitSet.of(0);
        var call = AggregateCall.create(org.apache.calcite.sql.fun.SqlStdOperatorTable.SUM, true, false,
                false, List.of(0), -1, null, org.apache.calcite.rel.RelCollations.EMPTY, 1, input, null, "SUM_DISTINCT");
        var before = LogicalAggregate.create(input, List.of(), groupSet, null, List.of(call));
        var expectedCall = AggregateCall.create(org.apache.calcite.sql.fun.SqlStdOperatorTable.SUM, false, false,
                false, List.of(0), -1, null, org.apache.calcite.rel.RelCollations.EMPTY, 1, input, null, "SUM_DISTINCT");
        var after = LogicalAggregate.create(input, List.of(), groupSet, null, List.of(expectedCall));
        RuleChecks.rewrites(new EliminateAggFilteredDistinctForKeys(), before, after);
    }

    private static void rejectsFilteredAggregate() {
        var builder = RuleBuilder.create();
        var table = builder.createQedTable(Seq.of(Tuple.of(RelType.fromString("BOOLEAN", false), true)));
        builder.addTable(table);
        var b = RuleChecks.ruleBuilder(builder);
        var input = b.scan(table.getName()).build();
        var call = AggregateCall.create(org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT, true, false,
                false, List.of(0), 0, null, org.apache.calcite.rel.RelCollations.EMPTY, 1, input, null,
                "COUNT_DISTINCT_FILTERED");
        var filtered = LogicalAggregate.create(input, List.of(), ImmutableBitSet.of(0), null, List.of(call));
        RuleChecks.doesNotApply(new EliminateAggFilteredDistinctForKeys(), filtered);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAggFilteredDistinctForKeys test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.AggregateUnionAggregate;

import java.util.List;

public final class AggregateUnionAggregateTest {
    public static void runTest() {
        var rule = new AggregateUnionAggregate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var rawBuilder = org.qed.RuleBuilder.create();
        var left = RuleChecks.table(rawBuilder, 1, false);
        var right = RuleChecks.table(rawBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(rawBuilder);
        RelNode leftInput = builder.scan(left).build();
        RelNode rightInput = builder.scan(right).build();
        RelNode rightDistinct = LogicalAggregate.create(rightInput, List.of(), ImmutableBitSet.of(0), null, List.of());
        RelNode sourceUnion = LogicalUnion.create(List.of(leftInput, rightDistinct), true);
        RelNode before = LogicalAggregate.create(sourceUnion, List.of(), ImmutableBitSet.of(0), null, List.of());
        RelNode rewrittenUnion = LogicalUnion.create(List.of(leftInput, rightInput), true);
        RelNode after = LogicalAggregate.create(rewrittenUnion, List.of(), ImmutableBitSet.of(0), null, List.of());
        RuleChecks.rewrites(rule, before, after);

        var rawMissBuilder = org.qed.RuleBuilder.create();
        var missLeft = RuleChecks.table(rawMissBuilder, 1, false);
        var missRight = RuleChecks.table(rawMissBuilder, 1, false);
        var missBuilder = RuleChecks.ruleBuilder(rawMissBuilder);
        RelNode missLeftInput = missBuilder.scan(missLeft).build();
        RelNode missRightInput = missBuilder.scan(missRight).build();
        var count = AggregateCall.create(SqlStdOperatorTable.COUNT, false, false, false, List.of(0), -1,
                null, RelCollations.EMPTY, 0, missRightInput, null, "count_value");
        RelNode missInput = LogicalAggregate.create(missRightInput, List.of(), ImmutableBitSet.of(), null, List.of(count));
        RelNode missUnion = LogicalUnion.create(List.of(missLeftInput, missInput), true);
        RelNode nearMiss = LogicalAggregate.create(missUnion, List.of(), ImmutableBitSet.of(0), null, List.of());
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateUnionAggregate test...");
        runTest();
    }
}

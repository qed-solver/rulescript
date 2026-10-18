package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRule;
import org.qed.RRuleInstances.HoistSelectAboveUnorderedDistinctOn;
import org.qed.RuleBuilder;

public final class HoistSelectAboveUnorderedDistinctOnTest {
    public static void runTest() {
        var rule = new HoistSelectAboveUnorderedDistinctOn();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsAggregateWithAggregateCall(rule);
    }

    private static void rewritesHandBuiltPlan(RRule rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var input = b.scan(table).build();
        var ref = b.getRexBuilder().makeInputRef(input, 0);
        var condition = b.call(SqlStdOperatorTable.GREATER_THAN, ref, b.literal(10));
        var filter = LogicalFilter.create(input, condition);
        var before = LogicalAggregate.create(filter, java.util.List.of(), ImmutableBitSet.of(0, 1), null,
                java.util.List.of());
        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 2, false, false);
        var e = RuleChecks.ruleBuilder(expectedBuilder);
        var expectedInput = e.scan(expectedTable).build();
        var grouped = LogicalAggregate.create(expectedInput, java.util.List.of(), ImmutableBitSet.of(0, 1), null,
                java.util.List.of());
        var expectedRef = e.getRexBuilder().makeInputRef(grouped, 0);
        var expectedCondition = e.call(SqlStdOperatorTable.GREATER_THAN, expectedRef, e.literal(10));
        var expected = LogicalFilter.create(grouped, expectedCondition);
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsAggregateWithAggregateCall(RRule rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var input = b.scan(table).build();
        var ref = b.getRexBuilder().makeInputRef(input, 0);
        var condition = b.call(SqlStdOperatorTable.GREATER_THAN, ref, b.literal(10));
        var filter = LogicalFilter.create(input, condition);
        var count = org.apache.calcite.rel.core.AggregateCall.create(
                SqlStdOperatorTable.COUNT, false, false, false, java.util.List.of(), -1, null,
                org.apache.calcite.rel.RelCollations.EMPTY, 2, filter, null, "n");
        var plan = LogicalAggregate.create(filter, java.util.List.of(), ImmutableBitSet.of(0, 1), null,
                java.util.List.of(count));
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running HoistSelectAboveUnorderedDistinctOn test...");
        runTest();
    }
}

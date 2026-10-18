package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateExpandWithinDistinct;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import java.util.List;

public class AggregateExpandWithinDistinctTest {
    public static void runTest() {
        var rule = new AggregateExpandWithinDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var qedBuilder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 1, false, true);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        builder.scan(table);
        var count = builder.aggregateCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT, true, false,
                null, null, builder.field(0));
        var before = builder.aggregate(builder.groupKey(), List.of(count)).build();

        builder.scan(table);
        var deduplicated = builder.aggregate(builder.groupKey(builder.field(0)),
                List.<AggregateCall>of()).build();
        builder.push(deduplicated);
        var expectedCount = builder.aggregateCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT, false, false,
                null, null, builder.field(0));
        var expected = builder.aggregate(builder.groupKey(), List.of(expectedCount)).build();
        RuleChecks.rewrites(rule, before, expected);

        var input = RuleChecks.ruleBuilder(qedBuilder).scan(table).build();
        var nonDistinct = AggregateCall.create(org.apache.calcite.sql.fun.SqlStdOperatorTable.COUNT, false, false,
                false, List.of(0), -1, null, org.apache.calcite.rel.RelCollations.EMPTY, 0, input, null, null);
        var nearMiss = LogicalAggregate.create(input, List.of(), ImmutableBitSet.of(), null, List.of(nonDistinct));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateExpandWithinDistinct test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError(CalciteTester.failures() + " test failures");
        }
    }
}

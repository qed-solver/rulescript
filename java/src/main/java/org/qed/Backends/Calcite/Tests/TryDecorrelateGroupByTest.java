package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.TryDecorrelateGroupBy;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import java.util.List;

public class TryDecorrelateGroupByTest {
    public static void runTest() {
        var rule = new TryDecorrelateGroupBy();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch();
        meaningfulNoMatch();
    }

    private static void handBuiltMatch() {
        var builder = org.qed.RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, true, false);
        var r = RuleChecks.table(builder, 2, false, false);
        var planBuilder = RuleChecks.ruleBuilder(builder);
        var left = planBuilder.scan(l).build();
        var rightInput = planBuilder.scan(r).build();
        var calls = List.of(AggregateCall.create(SqlStdOperatorTable.SUM, false, false, false,
                List.of(1), -1, null, RelCollations.EMPTY, 1, rightInput, null, "f"));
        var grouped = LogicalAggregate.create(rightInput, List.of(), ImmutableBitSet.of(0), null, calls);
        var cond = planBuilder.getRexBuilder().makeLiteral(true);
        var joined = LogicalJoin.create(left, grouped, List.of(), cond, java.util.Set.of(), JoinRelType.INNER);
        var before = LogicalFilter.create(joined, cond);
        var expectedJoin = LogicalJoin.create(left, rightInput, List.of(), cond, java.util.Set.of(), JoinRelType.INNER);
        var expectedCall = AggregateCall.create(SqlStdOperatorTable.SUM, false, false, false,
                List.of(2), -1, null, RelCollations.EMPTY, 2, expectedJoin, null, "f");
        var expected = LogicalAggregate.create(expectedJoin, List.of(), ImmutableBitSet.of(0, 1), null,
                List.of(expectedCall));
        RuleChecks.rewrites(new TryDecorrelateGroupBy(), before, expected);
    }

    private static void meaningfulNoMatch() {
        var builder = org.qed.RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, true, false);
        var r = RuleChecks.table(builder, 2, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(l).build();
        var right = b.scan(r).build();
        var grouped = b.push(right).aggregate(b.groupKey(b.field(0)), b.count(false, "f")).build();
        var wrong = LogicalJoin.create(left, grouped, List.of(), b.getRexBuilder().makeLiteral(true),
                java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(new TryDecorrelateGroupBy(), LogicalFilter.create(wrong, b.getRexBuilder().makeLiteral(true)));
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateGroupBy test...");
        runTest();
        if (CalciteTester.failures() != 0) throw new AssertionError("Calcite rule test failed");
    }
}

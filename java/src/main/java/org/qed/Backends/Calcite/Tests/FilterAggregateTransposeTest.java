package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.FilterAggregateTranspose;
import org.qed.RelRN;
import org.qed.RuleBuilder;

import java.util.List;

public final class FilterAggregateTransposeTest {
    public static void runTest() {
        var rule = new FilterAggregateTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch(rule);
        handBuiltNoMatch();
    }

    private static void handBuiltMatch(FilterAggregateTranspose rule) {
        var ruleBuilder = RuleBuilder.create();
        var builder = ruleBuilder.transform(CalciteRuntime::configure);
        var keyTable = RuleChecks.table(ruleBuilder, 1, false);
        var valueTable = RuleChecks.table(ruleBuilder, 1, false);
        var input = builder.scan(keyTable).scan(valueTable).join(org.apache.calcite.rel.core.JoinRelType.INNER,
                builder.literal(true)).build();
        var rex = builder.getRexBuilder();
        var groupKey = ImmutableBitSet.of(0);
        var count = AggregateCall.create(SqlStdOperatorTable.COUNT, false, false, false, List.of(1), -1, null,
                RelCollations.EMPTY, 1, input, null, "c");
        var aggregate = LogicalAggregate.create(input, List.of(), groupKey, null, List.of(count));
        RexNode p = builder.call(SqlStdOperatorTable.GREATER_THAN, rex.makeInputRef(aggregate, 0), builder.literal(5));
        RexNode qa = builder.call(SqlStdOperatorTable.LESS_THAN, rex.makeInputRef(aggregate, 0), builder.literal(20));
        RexNode q = builder.call(SqlStdOperatorTable.GREATER_THAN, rex.makeInputRef(aggregate, 1), builder.literal(2));
        var before = LogicalFilter.create(aggregate, builder.call(SqlStdOperatorTable.AND, p, qa, q));

        var pushed = LogicalFilter.create(input,
                builder.call(SqlStdOperatorTable.GREATER_THAN, rex.makeInputRef(input, 0), builder.literal(5)));
        var expectedAggregate = LogicalAggregate.create(pushed, List.of(), groupKey, null, List.of(count));
        var expected = LogicalFilter.create(expectedAggregate,
                builder.call(SqlStdOperatorTable.AND,
                        builder.call(SqlStdOperatorTable.LESS_THAN, rex.makeInputRef(expectedAggregate, 0), builder.literal(20)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, rex.makeInputRef(expectedAggregate, 1), builder.literal(2))));
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void handBuiltNoMatch() {
        var ruleBuilder = RuleBuilder.create();
        var builder = ruleBuilder.transform(CalciteRuntime::configure);
        var keyTable = RuleChecks.table(ruleBuilder, 1, false);
        var valueTable = RuleChecks.table(ruleBuilder, 1, false);
        var input = builder.scan(keyTable).scan(valueTable).join(org.apache.calcite.rel.core.JoinRelType.INNER,
                builder.literal(true)).build();
        var rex = builder.getRexBuilder();
        var groupKey = ImmutableBitSet.of(0);
        var count = AggregateCall.create(SqlStdOperatorTable.COUNT, false, false, false, List.of(1), -1, null,
                RelCollations.EMPTY, 1, input, null, "c");
        var aggregate = LogicalAggregate.create(input, List.of(), groupKey, null, List.of(count));
        var condition = builder.call(SqlStdOperatorTable.GREATER_THAN, rex.makeInputRef(aggregate, 1), builder.literal(2));
        RuleChecks.doesNotApply(new FilterAggregateTranspose(), LogicalFilter.create(aggregate, condition));
    }

    public static void main(String[] args) {
        System.out.println("Running FilterAggregateTranspose test...");
        runTest();
    }
}

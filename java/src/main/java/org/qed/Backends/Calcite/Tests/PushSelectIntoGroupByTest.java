package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.PushSelectIntoGroupBy;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public final class PushSelectIntoGroupByTest {
    public static void runTest() {
        var rule = new PushSelectIntoGroupBy();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoGroupBy rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var input = builder.scan(table).build();
        var aggregateOperator = builder.genericAggregateOp("f", RelType.fromString("INTEGER", false));
        var predicatePOperator = builder.genericPredicateOp("P", false);
        var predicateROperator = builder.genericPredicateOp("R", false);
        var call = AggregateCall.create(aggregateOperator,
                false, false, false, List.of(1), -1, null, org.apache.calcite.rel.RelCollations.EMPTY,
                1, input, null, "f");
        var aggregate = LogicalAggregate.create(input, List.of(), ImmutableBitSet.of(0), null, List.of(call));
        var rexBuilder = aggregate.getCluster().getRexBuilder();
        var key = rexBuilder.makeInputRef(aggregate, 0);
        var condition = rexBuilder.makeCall(SqlStdOperatorTable.AND,
                rexBuilder.makeCall(predicatePOperator, key), rexBuilder.makeCall(predicateROperator, key));
        var plan = LogicalFilter.create(aggregate, condition);

        var plain = RuleChecks.ruleBuilder(builder);
        var expectedInput = plain.scan(table).build();
        var expectedKey = plain.getRexBuilder().makeInputRef(expectedInput, 0);
        var expectedP = plain.getRexBuilder().makeCall(predicatePOperator, expectedKey);
        var expectedR = plain.getRexBuilder().makeCall(predicateROperator, expectedKey);
        var expectedCondition = plain.getRexBuilder().makeCall(SqlStdOperatorTable.AND, expectedP, expectedR);
        var expectedCall = plain.aggregateCall(aggregateOperator, false, null, "f",
                List.of(plain.getRexBuilder().makeInputRef(expectedInput, 1)));
        var expected = plain.push(expectedInput).filter(expectedCondition)
                .aggregate(plain.groupKey(expectedKey), expectedCall).build();
        RuleChecks.rewrites(rule, plan, expected);

        var nullableBuilder = RuleBuilder.create();
        var nullableTable = RuleChecks.table(nullableBuilder, 2, false, true);
        var nullableInput = nullableBuilder.scan(nullableTable).build();
        var nullableCall = AggregateCall.create(nullableBuilder.genericAggregateOp("f", RelType.fromString("INTEGER", false)),
                false, false, false, List.of(1), -1, null, org.apache.calcite.rel.RelCollations.EMPTY,
                1, nullableInput, null, "f");
        var nullableAggregate = LogicalAggregate.create(nullableInput, List.of(), ImmutableBitSet.of(0), null,
                List.of(nullableCall));
        var nullableRexBuilder = nullableAggregate.getCluster().getRexBuilder();
        var nullableKey = nullableRexBuilder.makeInputRef(nullableAggregate, 0);
        var nullableCondition = nullableRexBuilder.makeCall(SqlStdOperatorTable.AND,
                nullableRexBuilder.makeCall(nullableBuilder.genericPredicateOp("P", false), nullableKey),
                nullableRexBuilder.makeCall(nullableBuilder.genericPredicateOp("R", false), nullableKey));
        RelNode nullablePlan = LogicalFilter.create(nullableAggregate, nullableCondition);
        RuleChecks.doesNotApply(rule, nullablePlan);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoGroupBy test...");
        runTest();
    }
}

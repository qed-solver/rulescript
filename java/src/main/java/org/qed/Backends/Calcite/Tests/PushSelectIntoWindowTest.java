package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushSelectIntoWindow;
import org.qed.RuleBuilder;

public class PushSelectIntoWindowTest {
    public static void runTest() {
        var rule = new PushSelectIntoWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoWindow rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var input = builder.scan(table).build();
        var key = builder.getRexBuilder().makeInputRef(input, 0);
        var p = builder.call(SqlStdOperatorTable.IS_NULL, key);
        var aggregate = builder.push(input).aggregate(builder.groupKey(0), builder.aggregateCall(
                builder.genericAggregateOp("w", org.qed.RelType.fromString("INTEGER", false)), false, null,
                "w", java.util.List.of(builder.field(1, 0, 1)))).build();
        var joinType = builder.getTypeFactory().builder().addAll(input.getRowType().getFieldList())
                .addAll(aggregate.getRowType().getFieldList()).build();
        var joinCondition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                builder.getRexBuilder().makeInputRef(joinType.getFieldList().get(0).getType(), 0),
                builder.getRexBuilder().makeInputRef(joinType.getFieldList().get(2).getType(), 2));
        var window = LogicalJoin.create(input, aggregate, java.util.List.of(), joinCondition,
                java.util.Set.of(), JoinRelType.INNER);
        var qOp = new RuleBuilder.QedFunction("q", org.qed.RelType.fromString("BOOLEAN", false));
        var q = builder.call(qOp, builder.getRexBuilder().makeInputRef(window, 0),
                builder.getRexBuilder().makeInputRef(window, 1), builder.getRexBuilder().makeInputRef(window, 3));
        var plan = LogicalFilter.create(window, builder.call(SqlStdOperatorTable.AND, p, q));

        var plain = RuleChecks.ruleBuilder(builder);
        var filtered = plain.push(input).filter(plain.call(SqlStdOperatorTable.IS_NULL,
                plain.getRexBuilder().makeInputRef(input, 0))).build();
        var expectedAggregate = plain.push(filtered).aggregate(plain.groupKey(0), plain.aggregateCall(
                builder.genericAggregateOp("w", org.qed.RelType.fromString("INTEGER", false)), false, null,
                "w", java.util.List.of(plain.field(1, 0, 1)))).build();
        var expectedJoinType = plain.getTypeFactory().builder().addAll(filtered.getRowType().getFieldList())
                .addAll(expectedAggregate.getRowType().getFieldList()).build();
        var expectedJoin = LogicalJoin.create(filtered, expectedAggregate, java.util.List.of(),
                plain.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                        plain.getRexBuilder().makeInputRef(expectedJoinType.getFieldList().get(0).getType(), 0),
                        plain.getRexBuilder().makeInputRef(expectedJoinType.getFieldList().get(2).getType(), 2)),
                java.util.Set.of(), JoinRelType.INNER);
        var expectedQ = plain.call(qOp, plain.getRexBuilder().makeInputRef(expectedJoin, 0),
                plain.getRexBuilder().makeInputRef(expectedJoin, 1), plain.getRexBuilder().makeInputRef(expectedJoin, 3));
        var expected = LogicalFilter.create(expectedJoin, expectedQ);
        RuleChecks.rewrites(rule, plan, expected);

        var leftJoin = LogicalJoin.create(input, aggregate, java.util.List.of(), joinCondition,
                java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, LogicalFilter.create(leftJoin, builder.call(SqlStdOperatorTable.AND, p,
                builder.call(qOp, builder.getRexBuilder().makeInputRef(leftJoin, 0),
                        builder.getRexBuilder().makeInputRef(leftJoin, 1), builder.getRexBuilder().makeInputRef(leftJoin, 3)))));
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoWindow test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateExpandDistinctAggregates;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateExpandDistinctAggregatesTest {

    public static void runTest() {
        var rule = new AggregateExpandDistinctAggregates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateExpandDistinctAggregates rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        b.scan(t);
        var c = b.field(0);
        var k = b.call(SqlStdOperatorTable.PLUS, c, b.literal(1));
        var x = b.call(SqlStdOperatorTable.MULTIPLY, c, b.literal(2));
        var before = b.aggregate(b.groupKey(k),
                b.aggregateCall(SqlStdOperatorTable.SUM, true, (RexNode) null, (String) null, x),
                b.aggregateCall(SqlStdOperatorTable.COUNT, true, (RexNode) null, (String) null, x)).build();

        b.scan(t);
        c = b.field(0);
        k = b.call(SqlStdOperatorTable.PLUS, c, b.literal(1));
        x = b.call(SqlStdOperatorTable.MULTIPLY, c, b.literal(2));
        b.aggregate(b.groupKey(k, x));
        var after = b.aggregate(b.groupKey(b.field(0)), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1)),
                b.aggregateCall(SqlStdOperatorTable.COUNT, b.field(1))).build();
        RuleChecks.rewrites(rule, before, after);

        b.scan(t);
        c = b.field(0);
        k = b.call(SqlStdOperatorTable.PLUS, c, b.literal(1));
        x = b.call(SqlStdOperatorTable.MULTIPLY, c, b.literal(2));
        RuleChecks.doesNotApply(rule, b.aggregate(b.groupKey(k), b.aggregateCall(SqlStdOperatorTable.SUM, x),
                b.aggregateCall(SqlStdOperatorTable.COUNT, x)).build());

        b.scan(t);
        c = b.field(0);
        k = b.call(SqlStdOperatorTable.PLUS, c, b.literal(1));
        x = b.call(SqlStdOperatorTable.MULTIPLY, c, b.literal(2));
        var y = b.call(SqlStdOperatorTable.MULTIPLY, c, b.literal(3));
        RuleChecks.doesNotApply(rule, b.aggregate(b.groupKey(k),
                b.aggregateCall(SqlStdOperatorTable.SUM, true, (RexNode) null, (String) null, x),
                b.aggregateCall(SqlStdOperatorTable.COUNT, true, (RexNode) null, (String) null, y)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateExpandDistinctAggregates test...");
        runTest();
    }
}

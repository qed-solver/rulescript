package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushAggDistinctIntoGroupBy;
import org.qed.RuleBuilder;

public class PushAggDistinctIntoGroupByTest {

    public static void runTest() {
        var rule = new PushAggDistinctIntoGroupBy();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTable(rule);
    }

    private static void ordinaryTable(PushAggDistinctIntoGroupBy rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(table)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)),
                        b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)),
                        b.field(0))
                .aggregate(b.groupKey(0, 1), b.aggregateCall(SqlStdOperatorTable.COUNT, true, (org.apache.calcite.rex.RexNode) null, (String) null, b.field(2)))
                .build();
        var after = b.scan(table)
                .aggregate(b.groupKey(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)),
                        b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)),
                        b.field(0)))
                .aggregate(b.groupKey(0, 1), b.aggregateCall(SqlStdOperatorTable.COUNT, b.field(2)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var plain = b.scan(table)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)),
                        b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)),
                        b.field(0))
                .aggregate(b.groupKey(0, 1), b.aggregateCall(SqlStdOperatorTable.COUNT, b.field(2)))
                .build();
        RuleChecks.doesNotApply(rule, plain);
    }

    public static void main(String[] args) {
        System.out.println("Running PushAggDistinctIntoGroupBy test...");
        runTest();
    }
}

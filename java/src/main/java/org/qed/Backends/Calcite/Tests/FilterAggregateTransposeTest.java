package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterAggregateTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterAggregateTransposeTest {

    public static void runTest() {
        var rule = new FilterAggregateTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FilterAggregateTranspose rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var grouped = (java.util.function.Supplier<RelBuilder>) () -> b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.literal(true))
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MAX, b.field(1)));
        var before = grouped.get().filter(b.and(RuleChecks.gt(b, b.field(0), 5),
                b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(100)), RuleChecks.gt(b, b.field(1), 0)))
                .build();
        var after = b.scan(l).scan(r).join(JoinRelType.INNER, b.literal(true)).filter(RuleChecks.gt(b, b.field(0), 5))
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MAX, b.field(1)))
                .filter(b.and(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(100)),
                        RuleChecks.gt(b, b.field(1), 0))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, grouped.get().filter(b.and(RuleChecks.gt(b, b.field(1), 5),
                b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(100)), RuleChecks.gt(b, b.field(1), 0)))
                .build());
        RuleChecks.doesNotApply(rule, grouped.get().filter(b.and(RuleChecks.gt(b, b.field(0), 5),
                RuleChecks.gt(b, b.field(1), 0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FilterAggregateTranspose test...");
        runTest();
    }
}

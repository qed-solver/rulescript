package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateFilterTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateFilterTransposeTest {

    public static void runTest() {
        var rule = new AggregateFilterTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateFilterTranspose rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var join = (java.util.function.Supplier<RelBuilder>) () ->
                b.scan(l).scan(r).join(JoinRelType.INNER, b.literal(true));
        var before = join.get().filter(RuleChecks.gt(b, b.field(0), 5))
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MAX, b.field(1))).build();
        var after = join.get().aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MAX, b.field(1)))
                .filter(RuleChecks.gt(b, b.field(0), 5)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, join.get().filter(RuleChecks.gt(b, b.field(1), 5))
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MAX, b.field(1))).build());
        RuleChecks.doesNotApply(rule, join.get()
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MAX, b.field(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateFilterTranspose test...");
        runTest();
    }
}

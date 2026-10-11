package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateProjectPullUpConstants;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateProjectPullUpConstantsTest {

    public static void runTest() {
        var rule = new AggregateProjectPullUpConstants();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateProjectPullUpConstants rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).project(List.of(b.literal(true), b.field(0)), List.of(), true)
                .aggregate(b.groupKey(0, 1), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1))).build();
        var after = b.scan(t).project(List.of(b.literal(true), b.field(0)), List.of(), true)
                .aggregate(b.groupKey(1), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1)))
                .project(b.literal(true), b.field(0), b.field(1)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).project(List.of(b.literal(false), b.field(0)), List.of(), true)
                .aggregate(b.groupKey(0, 1), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1))).build());
        RuleChecks.doesNotApply(rule, b.scan(t).project(List.of(b.field(0), b.field(0)), List.of(), true)
                .aggregate(b.groupKey(0, 1), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateProjectPullUpConstants test...");
        runTest();
    }
}

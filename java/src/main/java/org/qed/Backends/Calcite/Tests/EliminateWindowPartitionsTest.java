package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.EliminateWindowPartitions;

import java.util.List;

public final class EliminateWindowPartitionsTest {
    public static void runTest() {
        var rule = new EliminateWindowPartitions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsHandBuiltNearMiss(rule);
    }

    private static void rewritesHandBuiltPlan(EliminateWindowPartitions rule) {
        var b = builder();
        var table = RuleChecks.table(b, 2, false);
        var source = b.scan(table).build();

        b.push(source);
        b.project(List.of(b.field(0), b.literal(0)), List.of(), true);
        var projected = b.build();
        b.push(projected);
        b.aggregate(b.groupKey(b.field(0), b.field(1)),
                b.aggregateCall(SqlStdOperatorTable.COUNT, false, null, "count", List.of(b.field(0))));
        var before = b.build();

        b.scan(table);
        b.aggregate(b.groupKey(b.field(0)),
                b.aggregateCall(SqlStdOperatorTable.COUNT, false, null, "count", List.of(b.field(0))));
        b.project(List.of(b.field(0), b.literal(0), b.field(1)));
        var after = b.build();
        RuleChecks.rewrites(rule, before, after);
    }

    private static void rejectsHandBuiltNearMiss(EliminateWindowPartitions rule) {
        var b = builder();
        var table = RuleChecks.table(b, 2, false);
        var source = b.scan(table).build();
        b.push(source);
        b.project(List.of(b.field(0), b.literal(1)), List.of(), true);
        b.aggregate(b.groupKey(b.field(0), b.field(1)),
                b.aggregateCall(SqlStdOperatorTable.COUNT, false, null, "count", List.of(b.field(0))));
        RuleChecks.doesNotApply(rule, b.build());
    }

    private static RuleBuilder builder() {
        return RuleBuilder.create();
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateWindowPartitions test...");
        runTest();
    }
}

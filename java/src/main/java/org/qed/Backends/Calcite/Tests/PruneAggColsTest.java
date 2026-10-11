package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PruneAggCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneAggColsTest {

    public static void runTest() {
        var rule = new PruneAggCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneAggCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 3, false);
        var f = base.genericAggregateOp("f", new RelType.VarType("F_Type", true));
        var g = base.genericAggregateOp("g", new RelType.VarType("G_Type", true));
        var b = RuleChecks.ruleBuilder(base);

        var before = base.scan(t)
                .aggregate(base.groupKey(0), base.aggregateCall(f, base.field(1)), base.aggregateCall(g, base.field(2)))
                .project(List.of(base.field(0), base.field(1)))
                .build();
        var after = b.scan(t)
                .aggregate(b.groupKey(0), b.aggregateCall(f, b.field(1)))
                .project(List.of(b.field(0), b.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, base.scan(t)
                .aggregate(base.groupKey(0), base.aggregateCall(f, base.field(1)), base.aggregateCall(g, base.field(2)))
                .project(List.of(base.field(0), base.field(2)))
                .build());
        RuleChecks.doesNotApply(rule, base.scan(t)
                .aggregate(base.groupKey(0, 1), base.aggregateCall(f, base.field(1)), base.aggregateCall(g, base.field(2)))
                .project(List.of(base.field(0), base.field(1)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneAggCols test...");
        runTest();
    }
}

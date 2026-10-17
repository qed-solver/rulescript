package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.OptimizeOneRowPlan;
import org.qed.RuleBuilder;

public class OptimizeOneRowPlanTest {
    public static void runTest() {
        var rule = new OptimizeOneRowPlan();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(OptimizeOneRowPlan rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, true, false);
        var calciteBuilder = RuleChecks.ruleBuilder(builder);
        var scan = calciteBuilder.scan(table).build();
        var before = LogicalAggregate.create(scan, java.util.List.of(), ImmutableBitSet.of(0), null,
                java.util.List.of());
        var after = calciteBuilder.scan(table).project(calciteBuilder.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        var repeated = RuleBuilder.create();
        var ordinary = RuleChecks.table(repeated, 1, false, false);
        var nearMiss = repeated.scan(ordinary).aggregate(repeated.groupKey(0), Seq.empty()).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running OptimizeOneRowPlan test...");
        runTest();
    }
}

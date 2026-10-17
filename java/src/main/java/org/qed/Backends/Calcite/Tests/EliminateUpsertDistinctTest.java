package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateUpsertDistinct;
import org.qed.RuleBuilder;

import java.util.List;

public final class EliminateUpsertDistinctTest {
    public static void runTest() {
        var rule = new EliminateUpsertDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsNonUniqueHandBuiltPlan();
    }

    private static void rewritesHandBuiltPlan() {
        var builder = builder();
        var table = RuleChecks.table(builder, 1, true, true);
        var input = builder.scan(table).build();
        var groupSet = ImmutableBitSet.of(0);
        var before = LogicalAggregate.create(input, List.of(), groupSet, null, List.of());

        var expectedBuilder = builder();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, true, true);
        var expected = expectedBuilder.scan(expectedTable).project(expectedBuilder.field(0)).build();
        RuleChecks.rewrites(rule(), before, expected);
    }

    private static void rejectsNonUniqueHandBuiltPlan() {
        var builder = builder();
        var table = RuleChecks.table(builder, 1, false, true);
        var input = builder.scan(table).build();
        var before = LogicalAggregate.create(input, List.of(), ImmutableBitSet.of(0), null, List.of());
        RuleChecks.doesNotApply(rule(), before);
    }

    private static RuleBuilder builder() {
        return RuleBuilder.create();
    }

    private static EliminateUpsertDistinct rule() {
        return new EliminateUpsertDistinct();
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateUpsertDistinct test...");
        runTest();
    }
}

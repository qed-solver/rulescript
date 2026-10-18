package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.EliminateDistinct;

public final class EliminateDistinctTest {
    public static void runTest() {
        var rule = new EliminateDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltUniqueKeyPlan();
        rejectsHandBuiltNonKeyDistinct();
    }

    private static void rewritesHandBuiltUniqueKeyPlan() {
        var schema = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(schema, 1, true, false);
        var builder = RuleChecks.ruleBuilder(schema);
        var expected = builder.scan(table).build();
        var actualInput = builder.scan(table).build();
        var group = ImmutableBitSet.of(0);
        var before = LogicalAggregate.create(actualInput, java.util.List.of(), group, null, java.util.List.of());
        RuleChecks.rewrites(new EliminateDistinct(), before, expected);
    }

    private static void rejectsHandBuiltNonKeyDistinct() {
        var schema = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(schema, 1, false, false);
        var builder = RuleChecks.ruleBuilder(schema);
        var input = builder.scan(table).build();
        var before = LogicalAggregate.create(input, java.util.List.of(), ImmutableBitSet.of(0), null,
                java.util.List.of());
        RuleChecks.doesNotApply(new EliminateDistinct(), before);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateDistinct test...");
        runTest();
    }
}

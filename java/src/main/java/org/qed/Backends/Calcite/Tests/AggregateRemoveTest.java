package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateRemove;
import org.qed.RuleBuilder;

public class AggregateRemoveTest {
    public static void runTest() {
        var rule = new AggregateRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        concreteUniqueKey();
        nonUniqueKey();
    }

    private static void concreteUniqueKey() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, true);
        var inputBuilder = RuleChecks.ruleBuilder(builder);
        var input = inputBuilder.scan(table).build();
        var actual = org.apache.calcite.rel.logical.LogicalAggregate.create(input, java.util.List.of(),
                org.apache.calcite.util.ImmutableBitSet.of(0), null, java.util.List.of());
        var expected = input;
        RuleChecks.rewrites(new AggregateRemove(), actual, expected);
    }

    private static void nonUniqueKey() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var inputBuilder = RuleChecks.ruleBuilder(builder);
        var input = inputBuilder.scan(table).build();
        var actual = org.apache.calcite.rel.logical.LogicalAggregate.create(input, java.util.List.of(),
                org.apache.calcite.util.ImmutableBitSet.of(0), null, java.util.List.of());
        RuleChecks.doesNotApply(new AggregateRemove(), actual);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateRemove test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("AggregateRemove failures: " + CalciteTester.failures());
        }
    }
}

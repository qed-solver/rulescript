package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.UnionToDistinct;
import org.qed.RuleBuilder;

import java.util.List;

public final class UnionToDistinctTest {
    public static void runTest() {
        var rule = new UnionToDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        independentlyBuiltMatch(rule);
        meaningfulNoMatch(rule);
    }

    private static void independentlyBuiltMatch(UnionToDistinct rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false);
        var rightName = RuleChecks.table(builder, 1, false);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var source = org.apache.calcite.rel.logical.LogicalUnion.create(List.of(left, right), false);
        var unionAll = org.apache.calcite.rel.logical.LogicalUnion.create(List.of(left, right), true);
        var expected = org.apache.calcite.rel.logical.LogicalAggregate.create(unionAll, List.of(),
                org.apache.calcite.util.ImmutableBitSet.of(0), null, List.of());
        RuleChecks.rewrites(rule, source, expected);
    }

    private static void meaningfulNoMatch(UnionToDistinct rule) {
        var builder = RuleBuilder.create();
        var tableName = RuleChecks.table(builder, 1, false);
        RuleChecks.doesNotApply(rule, builder.scan(tableName).build());
    }

    public static void main(String[] args) {
        System.out.println("Running UnionToDistinct test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("UnionToDistinct test failed");
        }
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateUnionTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public final class AggregateUnionTransposeTest {
    public static void runTest() {
        var rule = new AggregateUnionTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var tables = RuleBuilder.create();
        var left = RuleChecks.table(tables, 2, false);
        var right = RuleChecks.table(tables, 2, false);
        var builder = RuleChecks.ruleBuilder(tables);

        var beforeUnion = builder.scan(left).scan(right).union(true, 2).build();
        builder.push(beforeUnion);
        var before = dedup(builder);

        var expectedLeft = dedup(builder.scan(left).build(), builder);
        var expectedRight = dedup(builder.scan(right).build(), builder);
        var expectedUnion = builder.push(expectedLeft).push(expectedRight).union(true, 2).build();
        builder.push(expectedUnion);
        var expected = dedup(builder);

        RuleChecks.rewrites(rule, before, expected);

        var nearUnion = builder.scan(left).scan(right).union(false, 2).build();
        builder.push(nearUnion);
        RuleChecks.doesNotApply(rule, dedup(builder));
    }

    private static RelNode dedup(RelBuilder builder) {
        builder.aggregate(builder.groupKey(0, 1), List.<org.apache.calcite.rel.core.AggregateCall>of());
        return builder.build();
    }

    private static RelNode dedup(RelNode input, RelBuilder builder) {
        builder.push(input);
        return dedup(builder);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Running AggregateUnionTranspose test...");
        CalciteTester.main(new String[]{"AggregateUnionTranspose"});
    }
}

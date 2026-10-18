package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.rel.logical.LogicalValues;
import org.qed.RRuleInstances.EliminateDistinctSetLeft;

import java.util.List;

public final class EliminateDistinctSetLeftTest {
    public static void runTest() {
        var rule = new EliminateDistinctSetLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateDistinctSetLeft rule) {
        var builder = org.qed.RuleBuilder.create();
        var left = RuleChecks.table(builder, 3, false);
        var right = RuleChecks.table(builder, 3, false);
        var input = builder.scan(left).project(builder.field(2), builder.field(0), builder.field(1)).build();
        var empty = LogicalValues.createEmpty(builder.getCluster(), input.getRowType());
        var plan = LogicalUnion.create(List.of(input, empty), false);

        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(left).project(plain.field(2), plain.field(0), plain.field(1))
                .aggregate(plain.groupKey(plain.field(0), plain.field(1), plain.field(2))).build();
        RuleChecks.rewrites(rule, plan, expected);

        var nonempty = builder.scan(right).project(builder.field(2), builder.field(0), builder.field(1)).build();
        RuleChecks.doesNotApply(rule, LogicalUnion.create(List.of(input, nonempty), false));
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateDistinctSetLeft test...");
        runTest();
    }
}

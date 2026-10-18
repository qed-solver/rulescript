package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalUnion;
import org.qed.RRuleInstances.UnionEliminator;
import org.qed.RuleBuilder;

public final class UnionEliminatorTest {
    public static void runTest() {
        var rule = new UnionEliminator();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var source = builder.scan(table).build();
        var concreteBefore = LogicalUnion.create(java.util.List.of(source), true);
        var expected = builder.scan(table).build();
        RuleChecks.rewrites(rule, concreteBefore, expected);

        var distinctUnion = LogicalUnion.create(java.util.List.of(source), false);
        RuleChecks.doesNotApply(rule, distinctUnion);
    }

    public static void main(String[] args) {
        System.out.println("Running UnionEliminator test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.AggregateRemove;
import org.qed.RuleBuilder;

public class AggregateRemoveTest {

    public static void runTest() {
        var rule = new AggregateRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateRemove rule) {
        var builder = RuleBuilder.create();
        var keyed = RuleChecks.table(builder, 1, true);
        var plain = RuleChecks.table(builder, 1, false);
        var distinctOverKey = org.apache.calcite.rel.logical.LogicalAggregate.create(builder.scan(keyed).build(),
                java.util.List.of(), org.apache.calcite.util.ImmutableBitSet.of(0), null, java.util.List.of());
        RuleChecks.rewrites(rule, distinctOverKey, builder.scan(keyed).build());
        var distinctOverColumn = org.apache.calcite.rel.logical.LogicalAggregate.create(builder.scan(plain).build(),
                java.util.List.of(), org.apache.calcite.util.ImmutableBitSet.of(0), null, java.util.List.of());
        RuleChecks.doesNotApply(rule, distinctOverColumn);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateRemove test...");
        runTest();
    }
}

package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlAggFunction;
import org.qed.RRuleInstances.EliminateAggDistinct;
import org.qed.RuleBuilder;

public final class EliminateAggDistinctTest {
    public static void runTest() {
        var rule = new EliminateAggDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        RuleChecks.rewrites(rule, concrete(true, true), concrete(true, false));
        RuleChecks.doesNotApply(rule, concrete(false, true));
    }

    private static RelNode concrete(boolean unique, boolean distinct) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, unique, false);
        var rel = RuleChecks.ruleBuilder(builder);
        rel.scan(table);
        var aggregate = builder.genericAggregateOp("f", org.qed.RelType.fromString("INTEGER", true));
        var call = rel.aggregateCall((SqlAggFunction) aggregate, distinct, null, "value", rel.field(0));
        return rel.aggregate(rel.groupKey(rel.field(0)), call).build();
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAggDistinct test...");
        runTest();
    }
}

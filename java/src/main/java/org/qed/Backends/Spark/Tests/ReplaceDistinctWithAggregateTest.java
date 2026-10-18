package org.qed.spark;

import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaceDistinctWithAggregateTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ReplaceDistinctWithAggregate.json"));

    @Test
    void distinctOverTwoNonNullColumnsBecomesGroupByAllColumns() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var plan = new Intersect(base, base, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var aggregate = (Aggregate) rewritten;
        assertTrue(aggregate.child() == base);
        assertEquals(Catalyst.list(base.output()), Catalyst.list(aggregate.groupingExpressions()));
        assertEquals(Catalyst.list(base.output()), Catalyst.list(aggregate.aggregateExpressions()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnAllVariantIntersectAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var plan = new Intersect(base, base, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANullableColumnAlone() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var plan = new Intersect(base, base, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAThreeColumnDistinctAlone() {
        var base = Support.relation("t", 3, List.of(0, 1, 2), new Random(1));
        var plan = new Intersect(base, base, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAUnionAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var plan = new Union(Catalyst.seq(List.<LogicalPlan>of(base, base)), false, false);
        assertEquals(plan, rule.apply(plan));
    }
}

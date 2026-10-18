package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
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

class ReplaceDeduplicateWithAggregateTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ReplaceDeduplicateWithAggregate.json"));

    @Test
    void distinctSelfIntersectBecomesGroupByAllColumns() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var plan = new Intersect(base, base, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var aggregate = (Aggregate) rewritten;
        assertTrue(aggregate.child() == base);
        var output = Catalyst.<Attribute>list(base.output());
        assertEquals(output, Catalyst.list(aggregate.groupingExpressions()));
        assertEquals(output, Catalyst.list(aggregate.aggregateExpressions()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void nullableColumnsAreAlsoGroupedAndCompared() {
        var base = Support.relation("n", 2, List.of(), new Random(7));
        var plan = new Intersect(base, base, false);
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof Aggregate);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void intersectAllIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var plan = new Intersect(base, base, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void intersectOfDifferentInputsIsLeftAlone() {
        var left = Support.relation("t", 2, List.of(), new Random(1));
        var right = Support.relation("u", 2, List.of(), new Random(2));
        var plan = new Intersect(left, right, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void intersectOfFilteredSideIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var filtered = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Intersect(base, filtered, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void unionDistinctIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(base, base)), false, false);
        assertEquals(plan, rule.apply(plan));
    }
}

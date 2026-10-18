package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectToExistsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("IntersectToExists.json"));

    @Test
    void distinctIntersectBecomesSemiJoinAggregate() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var plan = new Intersect(a, b, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate agg && agg.child() instanceof Join join
                && join.joinType().equals(LeftSemi$.MODULE$)
                && join.left().equals(a) && join.right().equals(b)
                && join.condition().isDefined() && join.condition().get() instanceof EqualNullSafe);
        var outputBefore = Catalyst.<Attribute>list(plan.output());
        var outputAfter = Catalyst.<Attribute>list(rewritten.output());
        assertEquals(outputBefore.get(0).exprId(), outputAfter.get(0).exprId());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void nullableColumnsIntersectLikeSemiJoin() {
        var a = Support.relation("a", 1, List.of(), new Random(7));
        var b = Support.relation("b", 1, List.of(), new Random(8));
        var plan = new Intersect(a, b, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate agg && agg.child() instanceof Join join
                && join.condition().get() instanceof EqualNullSafe);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void intersectAllIsLeftAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var plan = new Intersect(a, b, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void unionIsLeftAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(a, b)), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void multiColumnIntersectIsLeftAlone() {
        var a = Support.relation("a", 2, List.of(), new Random(1));
        var b = Support.relation("b", 2, List.of(), new Random(2));
        var plan = new Intersect(a, b, false);
        assertEquals(plan, rule.apply(plan));
    }
}

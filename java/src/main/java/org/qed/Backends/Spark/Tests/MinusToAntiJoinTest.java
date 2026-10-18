package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Except;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinusToAntiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("MinusToAntiJoin.json"));

    @Test
    void rewritesExceptIntoDistinctAntiJoin() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var plan = new Except(a, b, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var aggregate = (Aggregate) rewritten;
        assertTrue(aggregate.child() instanceof Join);
        var join = (Join) aggregate.child();
        assertEquals(LeftAnti$.MODULE$, join.joinType());
        assertEquals(a, join.left());
        assertEquals(b, join.right());
        assertTrue(join.condition().isDefined() && join.condition().get() instanceof EqualNullSafe);
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        assertEquals(List.of(x), Catalyst.list(aggregate.groupingExpressions()));
        assertEquals(List.of(x), Catalyst.list(aggregate.aggregateExpressions()));
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsResultsEqualOnRandomRowsWithNulls() {
        Assumptions.assumeTrue(Support.canExecute());
        for (int seed = 1; seed <= 15; seed++) {
            var a = Support.relation("A", 1, List.of(), new Random(seed));
            var b = Support.relation("B", 1, List.of(), new Random(seed + 100));
            var plan = new Except(a, b, false);
            var rewritten = rule.apply(plan);
            assertNotEquals(plan, rewritten);
            assertEquals(Support.run(plan), Support.run(rewritten), "seed " + seed);
        }
    }

    @Test
    void leavesExceptAllAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var plan = new Except(a, b, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIntersectAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var plan = new Intersect(a, b, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoColumnExceptAlone() {
        var a = Support.relation("A", 2, List.of(), new Random(1));
        var b = Support.relation("B", 2, List.of(), new Random(2));
        var plan = new Except(a, b, false);
        assertEquals(plan, rule.apply(plan));
    }
}

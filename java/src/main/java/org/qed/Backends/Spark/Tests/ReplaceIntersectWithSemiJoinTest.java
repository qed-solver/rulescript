package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaceIntersectWithSemiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ReplaceIntersectWithSemiJoin.json"));

    @Test
    void intersectBecomesSemiJoinOnNullSafeEquality() {
        var a = Support.relation("a", 2, List.of(), new Random(1));
        var b = Support.relation("b", 2, List.of(), new Random(2));
        var a0 = Catalyst.<Attribute>list(a.output()).get(0);
        var a1 = Catalyst.<Attribute>list(a.output()).get(1);
        var b0 = Catalyst.<Attribute>list(b.output()).get(0);
        var b1 = Catalyst.<Attribute>list(b.output()).get(1);
        var plan = new Intersect(a, b, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var aggregate = (Aggregate) rewritten;
        assertEquals(List.of(a0, a1), Catalyst.list(aggregate.groupingExpressions()));
        var join = (Join) aggregate.child();
        assertEquals(LeftSemi$.MODULE$, join.joinType());
        assertEquals(a, join.left());
        assertEquals(b, join.right());
        var and = (And) join.condition().get();
        assertEquals(new EqualNullSafe(a0, b0), and.left());
        assertEquals(new EqualNullSafe(a1, b1), and.right());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesIntersectAllAlone() {
        var a = Support.relation("a", 2, List.of(), new Random(1));
        var b = Support.relation("b", 2, List.of(), new Random(2));
        var plan = new Intersect(a, b, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesThreeColumnIntersectAlone() {
        var a = Support.relation("a", 3, List.of(), new Random(1));
        var b = Support.relation("b", 3, List.of(), new Random(2));
        var plan = new Intersect(a, b, false);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectToSemiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("IntersectToSemiJoin.json"));

    @Test
    void intersectOfTwoSingleColumnRelationsBecomesDistinctSemiJoin() {
        LocalRelation left = Support.relation("a", 1, List.of(), new Random(1));
        LocalRelation right = Support.relation("b", 1, List.of(), new Random(2));
        var plan = new Intersect(left, right, false);
        var leftColumn = Catalyst.<Attribute>list(left.output()).get(0);
        var rightColumn = Catalyst.<Attribute>list(right.output()).get(0);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var aggregate = (Aggregate) rewritten;
        assertEquals(List.of(leftColumn), Catalyst.list(aggregate.groupingExpressions()));
        assertTrue(aggregate.child() instanceof Join);
        var join = (Join) aggregate.child();
        assertEquals(LeftSemi$.MODULE$, join.joinType());
        assertEquals(left, join.left());
        assertEquals(right, join.right());
        assertEquals(Catalyst.some(new EqualNullSafe(leftColumn, rightColumn)), join.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void intersectAllIsLeftAlone() {
        LocalRelation left = Support.relation("a", 1, List.of(), new Random(1));
        LocalRelation right = Support.relation("b", 1, List.of(), new Random(2));
        var plan = new Intersect(left, right, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void intersectOfTwoColumnRelationsIsOutsideTheSingleColumnScope() {
        LocalRelation left = Support.relation("a", 2, List.of(), new Random(3));
        LocalRelation right = Support.relation("b", 2, List.of(), new Random(4));
        var plan = new Intersect(left, right, false);
        assertEquals(plan, rule.apply(plan));
    }
}

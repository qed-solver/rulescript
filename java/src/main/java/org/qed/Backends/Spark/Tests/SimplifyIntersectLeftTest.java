package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import scala.Option;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyIntersectLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyIntersectLeft.json"));

    private static LogicalPlan distinctColumn(String name, List<Integer> values) {
        var attr = Support.attribute(name, false);
        var rows = new ArrayList<InternalRow>();
        for (int v : values) {
            rows.add(new GenericInternalRow(new Object[] {v}));
        }
        var base = new LocalRelation(Catalyst.seq(List.of(attr)), Catalyst.seq(rows), false, Match.noStream());
        return new Aggregate(Catalyst.<Expression>seq(List.of(attr)), Catalyst.<NamedExpression>seq(List.of(attr)), base,
                Option.<AggregateHint>empty());
    }

    @Test
    void intersectWithDistinctLeftBecomesSemiJoin() {
        var left = distinctColumn("L", List.of(0, 1, 2));
        var right = Support.relation("R", 1, List.of(0), new Random(7));
        var plan = new Intersect(left, right, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j
                && j.joinType() == LeftSemi$.MODULE$
                && j.left() == left
                && j.right() == right
                && j.condition().get() instanceof EqualNullSafe eq
                && eq.left().equals(Catalyst.list(left.output()).get(0))
                && eq.right().equals(Catalyst.list(right.output()).get(0)));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void intersectWithDistinctLeftAndOtherRightBecomesSemiJoin() {
        var left = distinctColumn("L", List.of(3, 1));
        var right = Support.relation("R", 1, List.of(0), new Random(11));
        var plan = new Intersect(left, right, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.joinType() == LeftSemi$.MODULE$ && j.left() == left && j.right() == right);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void intersectAllIsLeftAlone() {
        var left = distinctColumn("L", List.of(0, 1, 2));
        var right = Support.relation("R", 1, List.of(0), new Random(7));
        var plan = new Intersect(left, right, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nullableLeftColumnIsLeftAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(0), new Random(7));
        var plan = new Intersect(left, right, false);
        assertEquals(plan, rule.apply(plan));
    }
}

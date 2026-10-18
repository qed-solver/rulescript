package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import scala.Option;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyIntersectRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyIntersectRight.json"));

    private static LocalRelation distinctRelation(String name, List<Integer> values) {
        var attr = Support.attribute(name + "_0", false);
        var rows = new ArrayList<InternalRow>();
        for (int v : values) {
            rows.add(new GenericInternalRow(new Object[] {v}));
        }
        return new LocalRelation(Catalyst.seq(List.of(attr)), Catalyst.seq(rows), false, Match.noStream());
    }

    private static Aggregate uniqueRelation(String name, List<Integer> values) {
        var base = distinctRelation(name, values);
        var attr = Catalyst.<Attribute>list(base.output()).get(0);
        return new Aggregate(Catalyst.<Expression>seq(List.of(attr)), Catalyst.<NamedExpression>seq(List.of(attr)), base, Option.empty());
    }

    @Test
    void intersectWithUniqueRightBecomesSemiJoin() {
        var left = Support.relation("L", 1, List.of(0), new Random(1));
        var right = uniqueRelation("R", List.of(1, 1, 2, 3));
        var plan = new Intersect(left, right, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var leftAttr = Catalyst.<Attribute>list(left.output()).get(0);
        var rightAttr = Catalyst.<Attribute>list(right.output()).get(0);
        assertTrue(rewritten instanceof Project p
                && p.output().head().exprId().equals(leftAttr.exprId())
                && p.child() instanceof Join j
                && j.joinType() == LeftSemi$.MODULE$
                && j.left().equals(right)
                && j.right().equals(left)
                && j.condition().get() instanceof EqualNullSafe eq
                && eq.left().equals(rightAttr)
                && eq.right().equals(leftAttr));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void intersectAllIsLeftAlone() {
        var left = Support.relation("L", 1, List.of(0), new Random(1));
        var right = distinctRelation("R", List.of(1, 2, 3));
        var plan = new Intersect(left, right, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void intersectWithNonUniqueRightIsLeftAlone() {
        var left = Support.relation("L", 1, List.of(0), new Random(1));
        var right = distinctRelation("R", List.of(1, 1, 2));
        var plan = new Intersect(left, right, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void intersectWithUniqueOnlyOnLeftIsLeftAlone() {
        var left = uniqueRelation("L", List.of(1, 1, 2, 3));
        var right = Support.relation("R", 1, List.of(0), new Random(1));
        var plan = new Intersect(left, right, false);
        assertEquals(plan, rule.apply(plan));
    }
}

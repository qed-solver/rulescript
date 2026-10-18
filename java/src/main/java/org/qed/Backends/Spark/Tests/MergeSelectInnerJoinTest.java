package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeSelectInnerJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("MergeSelectInnerJoin.json"));

    @Test
    void mergesFilterIntoInnerJoinCondition() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var on = new EqualTo(l, r);
        var above = new GreaterThan(l, Literal$.MODULE$.apply(1));
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(on), JoinHint$.MODULE$.NONE());
        var plan = new Filter(above, join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.joinType().equals(Inner$.MODULE$) && j.left() == left
                && j.right() == right && j.condition().isDefined() && j.condition().get() instanceof And);
        var condition = (And) ((Join) rewritten).condition().get();
        assertEquals(on, condition.left());
        assertEquals(above, condition.right());
        assumeExecutable();
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinFilterAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(l, Literal$.MODULE$.apply(1)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterBelowJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var filteredLeft = new Filter(new GreaterThan(l, Literal$.MODULE$.apply(1)), left);
        var plan = new Join(filteredLeft, right, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    private static void assumeExecutable() {
        Assumptions.assumeTrue(Support.canExecute());
    }
}

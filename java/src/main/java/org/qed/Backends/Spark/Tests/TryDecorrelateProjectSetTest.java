package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryDecorrelateProjectSetTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryDecorrelateProjectSet.json"));

    private static Attribute first(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output()).get(0);
    }

    @Test
    void movesZipOutOfInnerJoinIntoFilterOverCrossJoin() {
        var l = Support.relation("l", 1, List.of(), new Random(1));
        var i = Support.relation("i", 1, List.of(), new Random(2));
        var e = Support.relation("e", 1, List.of(), new Random(3));
        var lc = first(l);
        var ic = first(i);
        var ec = first(e);
        var on = new LessThan(lc, ic);
        var zip = new Join(i, e, Inner$.MODULE$, Option.<Expression>apply(new GreaterThan(ic, ec)),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(l, zip, Inner$.MODULE$, Option.<Expression>apply(on), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter);
        var filter = (Filter) rewritten;
        assertEquals(on, filter.condition());
        assertTrue(filter.child() instanceof Join);
        var top = (Join) filter.child();
        assertEquals(Inner$.MODULE$, top.joinType());
        assertTrue(top.condition().isDefined());
        assertEquals(e, top.right());
        assertTrue(top.left() instanceof Join);
        var cross = (Join) top.left();
        assertEquals(l, cross.left());
        assertEquals(i, cross.right());
        assertTrue(cross.condition().isEmpty());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsLeftOuterTopJoin() {
        var l = Support.relation("l", 1, List.of(), new Random(4));
        var i = Support.relation("i", 1, List.of(), new Random(5));
        var e = Support.relation("e", 1, List.of(), new Random(6));
        var zip = new Join(i, e, Inner$.MODULE$, Option.<Expression>apply(new GreaterThan(first(i), first(e))),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(l, zip, LeftOuter$.MODULE$, Option.<Expression>apply(new LessThan(first(l), first(i))),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsLeftOuterZipJoin() {
        var l = Support.relation("l", 1, List.of(), new Random(7));
        var i = Support.relation("i", 1, List.of(), new Random(8));
        var e = Support.relation("e", 1, List.of(), new Random(9));
        var zip = new Join(i, e, LeftOuter$.MODULE$, Option.<Expression>apply(new GreaterThan(first(i), first(e))),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(l, zip, Inner$.MODULE$, Option.<Expression>apply(new LessThan(first(l), first(i))),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsWiderZipInput() {
        var l = Support.relation("l", 1, List.of(), new Random(10));
        var i = Support.relation("i", 1, List.of(), new Random(11));
        var e = Support.relation("e", 2, List.of(), new Random(12));
        var zip = new Join(i, e, Inner$.MODULE$, Option.<Expression>apply(new GreaterThan(first(i), first(e))),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(l, zip, Inner$.MODULE$, Option.<Expression>apply(new LessThan(first(l), first(i))),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

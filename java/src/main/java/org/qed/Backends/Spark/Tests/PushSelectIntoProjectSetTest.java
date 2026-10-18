package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
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

class PushSelectIntoProjectSetTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushSelectIntoProjectSet.json"));

    @Test
    void pushesTheInputConjunctBelowTheInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("e", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var e = Catalyst.<Attribute>list(right.output()).get(0);
        var membership = new EqualTo(l, e);
        var bound = new GreaterThan(l, Literal$.MODULE$.apply(1));
        var unbound = new LessThan(e, Literal$.MODULE$.apply(3));
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(membership), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(bound, unbound), join);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var top = (Filter) rewritten;
        assertEquals(unbound, top.condition());
        var newJoin = (Join) top.child();
        assertEquals(Inner$.MODULE$, newJoin.joinType());
        assertEquals(membership, newJoin.condition().get());
        var pushed = (Filter) newJoin.left();
        assertEquals(bound, pushed.condition());
        assertEquals(left, pushed.child());
        assertEquals(right, newJoin.right());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void pushesTheInputConjunctForAnotherRandomInstance() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("e", 1, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var e = Catalyst.<Attribute>list(right.output()).get(0);
        var membership = new EqualTo(l, e);
        var bound = new GreaterThan(l, Literal$.MODULE$.apply(0));
        var unbound = new LessThan(e, Literal$.MODULE$.apply(2));
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(membership), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(bound, unbound), join);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var top = (Filter) rewritten;
        assertEquals(unbound, top.condition());
        var newJoin = (Join) top.child();
        assertEquals(membership, newJoin.condition().get());
        assertEquals(bound, ((Filter) newJoin.left()).condition());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("e", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var e = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l, e)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(new GreaterThan(l, Literal$.MODULE$.apply(1)),
                new LessThan(e, Literal$.MODULE$.apply(3))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAFilterWithoutAnUnboundConjunctAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("e", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var e = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l, e)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(new GreaterThan(l, Literal$.MODULE$.apply(1)),
                new LessThan(l, Literal$.MODULE$.apply(3))), join);
        assertEquals(plan, rule.apply(plan));
    }
}

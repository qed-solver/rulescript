package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommuteConstInequalityTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CommuteConstInequality.json"));

    @Test
    void flipsLessThanAcrossAnInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(Literal$.MODULE$.apply(true)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new LessThan(l, r), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.condition() instanceof GreaterThan g
                && g.left().equals(r) && g.right().equals(l));
        var child = ((Filter) rewritten).child();
        assertTrue(child instanceof Join j && j.left().equals(left) && j.right().equals(right) && j.joinType().equals(Inner$.MODULE$)
                && (j.condition().isEmpty() || j.condition().get().equals(Literal$.MODULE$.apply(true))));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsTheFilterWhenTheOperatorIsGreaterThan() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(Literal$.MODULE$.apply(true)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(l, r), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsTheFilterWhenTheJoinIsNotInner() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(Literal$.MODULE$.apply(true)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new LessThan(l, r), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsTheFilterWhenTheLeftInputHasTwoColumns() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(Literal$.MODULE$.apply(true)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new LessThan(l, r), join);
        assertEquals(plan, rule.apply(plan));
    }
}

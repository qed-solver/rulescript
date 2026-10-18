package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecorrelateProjectSetTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("DecorrelateProjectSet.json"));

    @Test
    void movesTheEmittedPredicateIntoAStandaloneFilter() {
        var left = Support.relation("L", 1, java.util.List.of(), new Random(1));
        var emitted = Support.relation("E", 1, java.util.List.of(), new Random(2));
        var e = Catalyst.<Attribute>list(emitted.output()).get(0);
        var plan = new Join(left, emitted, Inner$.MODULE$,
                Option.apply(new GreaterThan(e, Literal$.MODULE$.apply(1))), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.joinType().equals(Inner$.MODULE$) && j.condition().isEmpty());
        var join = (Join) rewritten;
        assertEquals(left, join.left());
        assertTrue(join.right() instanceof Filter f && f.child().equals(emitted)
                && f.condition().equals(new GreaterThan(e, Literal$.MODULE$.apply(1))));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void doesNotFireOnALeftOuterJoin() {
        var left = Support.relation("L", 1, java.util.List.of(), new Random(1));
        var emitted = Support.relation("E", 1, java.util.List.of(), new Random(2));
        var e = Catalyst.<Attribute>list(emitted.output()).get(0);
        var plan = new Join(left, emitted, LeftOuter$.MODULE$,
                Option.apply(new GreaterThan(e, Literal$.MODULE$.apply(1))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void doesNotFireWhenThePredicateReadsTheInputColumn() {
        var left = Support.relation("L", 1, java.util.List.of(), new Random(1));
        var emitted = Support.relation("E", 1, java.util.List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var plan = new Join(left, emitted, Inner$.MODULE$,
                Option.apply(new GreaterThan(l, Literal$.MODULE$.apply(1))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void doesNotFireWhenTheEmittedSideHasTwoColumns() {
        var left = Support.relation("L", 1, java.util.List.of(), new Random(1));
        var emitted = Support.relation("E", 2, java.util.List.of(), new Random(2));
        var e = Catalyst.<Attribute>list(emitted.output()).get(0);
        var plan = new Join(left, emitted, Inner$.MODULE$,
                Option.apply(new GreaterThan(e, Literal$.MODULE$.apply(1))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

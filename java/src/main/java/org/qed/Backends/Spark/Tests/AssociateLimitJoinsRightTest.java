package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssociateLimitJoinsRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AssociateLimitJoinsRight.json"));

    @Test
    void reassociatesInnerJoinAboveLeftJoin() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var cx = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(ax, bx)), JoinHint$.MODULE$.NONE());
        var plan = new Join(c, ab, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(cx, ax)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Join outer
                && outer.joinType().equals(LeftOuter$.MODULE$) && outer.left() instanceof Join inner
                && inner.joinType().equals(Inner$.MODULE$) && inner.left().equals(a) && inner.right().equals(c)
                && outer.right().equals(b));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesInnerJoinWithPredicateOnNullSideAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(4));
        var b = Support.relation("b", 1, List.of(), new Random(5));
        var c = Support.relation("c", 1, List.of(), new Random(6));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var cx = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(ax, bx)), JoinHint$.MODULE$.NONE());
        var plan = new Join(c, ab, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(cx, bx)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOuterJoinAboveLeftJoinAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(7));
        var b = Support.relation("b", 1, List.of(), new Random(8));
        var c = Support.relation("c", 1, List.of(), new Random(9));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var cx = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(ax, bx)), JoinHint$.MODULE$.NONE());
        var plan = new Join(c, ab, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(cx, ax)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

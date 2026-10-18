package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
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

class LeftAssociateJoinsLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("LeftAssociateJoinsLeft.json"));

    @Test
    void reassociatesInnerJoinsToTheLeft() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                Option.<Expression>apply(new And(new EqualTo(x, z), new EqualTo(y, z))), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Join outer
                && outer.left() == b && outer.right() instanceof Join inner
                && inner.left() == a && inner.right() == c);
        var outerCond = ((Join) ((Project) rewritten).child()).condition().get();
        var innerCond = ((Join) ((Join) ((Project) rewritten).child()).right()).condition().get();
        assertEquals(new EqualTo(y, z), outerCond);
        assertEquals(new EqualTo(x, z), innerCond);
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterOuterJoinAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(4));
        var b = Support.relation("b", 1, List.of(), new Random(5));
        var c = Support.relation("c", 1, List.of(), new Random(6));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, LeftOuter$.MODULE$,
                Option.<Expression>apply(new And(new EqualTo(x, z), new EqualTo(y, z))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesUnboundConjunctOnInsideLeftAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(7));
        var b = Support.relation("b", 1, List.of(), new Random(8));
        var c = Support.relation("c", 1, List.of(), new Random(9));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                Option.<Expression>apply(new And(new EqualTo(x, z), new EqualTo(x, z))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinWithConditionAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(10));
        var b = Support.relation("b", 1, List.of(), new Random(11));
        var c = Support.relation("c", 1, List.of(), new Random(12));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(x, y)), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                Option.<Expression>apply(new And(new EqualTo(x, z), new EqualTo(y, z))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

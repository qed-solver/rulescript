package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MultiJoinOptimizeBushyTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("MultiJoinOptimizeBushy.json"));

    @Test
    void rewritesLeftDeepFourWayJoinToBushy() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var xa = Catalyst.<Attribute>list(a.output()).get(0);
        var xb = Catalyst.<Attribute>list(b.output()).get(0);
        var xc = Catalyst.<Attribute>list(c.output()).get(0);
        var xd = Catalyst.<Attribute>list(d.output()).get(0);
        var cond = new GreaterThan(Add$.MODULE$.apply(xa, xb, Add$.MODULE$.apply$default$3()), Add$.MODULE$.apply(xc, xd, Add$.MODULE$.apply$default$3()));
        var ab = new Join(a, b, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var abc = new Join(ab, c, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Join(abc, d, Inner$.MODULE$, Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var top = assertInstanceOf(Join.class, rewritten);
        assertEquals(ab, top.left());
        var cd = assertInstanceOf(Join.class, top.right());
        assertEquals(c, cd.left());
        assertEquals(d, cd.right());
        assertEquals(Inner$.MODULE$, top.joinType());
        assertEquals(cond, top.condition().get());
        assumeCanExecute();
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterTopJoinAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var xa = Catalyst.<Attribute>list(a.output()).get(0);
        var xb = Catalyst.<Attribute>list(b.output()).get(0);
        var xc = Catalyst.<Attribute>list(c.output()).get(0);
        var xd = Catalyst.<Attribute>list(d.output()).get(0);
        var cond = new GreaterThan(Add$.MODULE$.apply(xa, xb, Add$.MODULE$.apply$default$3()), Add$.MODULE$.apply(xc, xd, Add$.MODULE$.apply$default$3()));
        var ab = new Join(a, b, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var abc = new Join(ab, c, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Join(abc, d, LeftOuter$.MODULE$, Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinWithConditionAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var xa = Catalyst.<Attribute>list(a.output()).get(0);
        var xb = Catalyst.<Attribute>list(b.output()).get(0);
        var xc = Catalyst.<Attribute>list(c.output()).get(0);
        var xd = Catalyst.<Attribute>list(d.output()).get(0);
        var cond = new GreaterThan(Add$.MODULE$.apply(xa, xb, Add$.MODULE$.apply$default$3()), Add$.MODULE$.apply(xc, xd, Add$.MODULE$.apply$default$3()));
        var ab = new Join(a, b, Inner$.MODULE$, Catalyst.<Expression>some(new EqualTo(xa, xb)), JoinHint$.MODULE$.NONE());
        var abc = new Join(ab, c, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Join(abc, d, Inner$.MODULE$, Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    private static void assumeCanExecute() {
        Assumptions.assumeTrue(Support.canExecute());
    }
}

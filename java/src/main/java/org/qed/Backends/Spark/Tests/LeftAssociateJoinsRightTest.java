package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LeftAssociateJoinsRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("LeftAssociateJoinsRight.json"));

    @Test
    void reassociatesInnerJoinsAndKeepsBothConditions() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var cx = Catalyst.<Attribute>list(c.output()).get(0);
        var axCond = new EqualTo(ax, cx);
        var buCond = new EqualTo(bx, cx);
        var ab = new Join(a, b, Inner$.MODULE$, scala.Option.empty(), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                scala.Option.apply(new And(axCond, buCond)), JoinHint$.MODULE$.NONE());
        var bc = new Join(b, c, Inner$.MODULE$, scala.Option.apply(buCond), JoinHint$.MODULE$.NONE());
        var expected = new Join(a, bc, Inner$.MODULE$, scala.Option.apply(axCond), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var top = assertInstanceOf(Join.class, rewritten);
        assertEquals(Catalyst.list(a.output()), Catalyst.list(top.left().output()));
        var inner = assertInstanceOf(Join.class, top.right());
        assertEquals(Catalyst.list(b.output()), Catalyst.list(inner.left().output()));
        assertEquals(Catalyst.list(c.output()), Catalyst.list(inner.right().output()));
        assertEquals(List.of(axCond), Catalyst.conjuncts(top.condition().get()));
        assertEquals(List.of(buCond), Catalyst.conjuncts(inner.condition().get()));
        assertEquals(Catalyst.list(expected.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterTopJoinAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var cx = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, scala.Option.empty(), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, LeftOuter$.MODULE$,
                scala.Option.apply(new And(new EqualTo(ax, cx), new EqualTo(bx, cx))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinWithConditionOnInnerAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var ax = Catalyst.<Attribute>list(a.output()).get(0);
        var bx = Catalyst.<Attribute>list(b.output()).get(0);
        var cx = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, scala.Option.apply(new EqualTo(ax, bx)), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                scala.Option.apply(new And(new EqualTo(ax, cx), new EqualTo(bx, cx))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

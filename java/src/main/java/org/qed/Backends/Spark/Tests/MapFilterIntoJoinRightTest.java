package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
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

class MapFilterIntoJoinRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("MapFilterIntoJoinRight.json"));

    @Test
    void movesTheFilterOntoTheRightEquivalentColumn() {
        var left = Support.relation("a", 1, List.of(0), new Random(1));
        var right = Support.relation("b", 1, List.of(0), new Random(2));
        var ax = Catalyst.<Attribute>list(left.output()).get(0);
        var bx = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(ax, bx), new GreaterThan(ax, Literal$.MODULE$.apply(2)));
        var plan = new Join(left, right, Inner$.MODULE$, Option.apply(cond), JoinHint$.MODULE$.NONE());
        var expectedCond = new And(new EqualTo(ax, bx), new GreaterThan(bx, Literal$.MODULE$.apply(2)));
        var expected = new Join(left, right, Inner$.MODULE$, Option.apply(expectedCond), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Join.class, rewritten);
        assertEquals(expected, rewritten);
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOuterJoinAlone() {
        var left = Support.relation("a", 1, List.of(0), new Random(1));
        var right = Support.relation("b", 1, List.of(0), new Random(2));
        var ax = Catalyst.<Attribute>list(left.output()).get(0);
        var bx = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(ax, bx), new GreaterThan(ax, Literal$.MODULE$.apply(2)));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAFilterOnTheRightColumnAlone() {
        var left = Support.relation("a", 1, List.of(0), new Random(1));
        var right = Support.relation("b", 1, List.of(0), new Random(2));
        var ax = Catalyst.<Attribute>list(left.output()).get(0);
        var bx = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(ax, bx), new GreaterThan(bx, Literal$.MODULE$.apply(2)));
        var plan = new Join(left, right, Inner$.MODULE$, Option.apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANullableLeftColumnAlone() {
        var left = Support.relation("a", 1, List.of(), new Random(1));
        var right = Support.relation("b", 1, List.of(0), new Random(2));
        var ax = Catalyst.<Attribute>list(left.output()).get(0);
        var bx = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(ax, bx), new GreaterThan(ax, Literal$.MODULE$.apply(2)));
        var plan = new Join(left, right, Inner$.MODULE$, Option.apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

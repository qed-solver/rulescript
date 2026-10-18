package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoptOptimizeJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("LoptOptimizeJoin.json"));

    @Test
    void reordersInnerJoinsAndPushesTheACConjunct() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Catalyst.<Expression>some(Literal$.MODULE$.TrueLiteral()),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                Catalyst.<Expression>some(new And(new GreaterThan(x, y), new LessThan(x, z))),
                JoinHint$.MODULE$.NONE());
        var expected = new Project(Catalyst.<NamedExpression>seq(List.of(x, y, z)),
                new Join(
                        new Join(a, c, Inner$.MODULE$, Catalyst.<Expression>some(new LessThan(x, z)), JoinHint$.MODULE$.NONE()),
                        b, Inner$.MODULE$, Catalyst.<Expression>some(new GreaterThan(x, y)), JoinHint$.MODULE$.NONE()));

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Join top && top.right() == b
                && top.left() instanceof Join inner && inner.left() == a && inner.right() == c);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOuterRootJoinAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Catalyst.<Expression>some(Literal$.MODULE$.TrueLiteral()),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, LeftOuter$.MODULE$,
                Catalyst.<Expression>some(new And(new GreaterThan(x, y), new LessThan(x, z))),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAFilteredInnerJoinAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Catalyst.<Expression>some(new GreaterThan(x, y)),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                Catalyst.<Expression>some(new And(new GreaterThan(x, y), new LessThan(x, z))),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesADisjunctiveRootConditionAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Catalyst.<Expression>some(Literal$.MODULE$.TrueLiteral()),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                Catalyst.<Expression>some(new Or(new GreaterThan(x, y), new LessThan(x, z))),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

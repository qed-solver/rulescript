package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.LessThanOrEqual;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DphypJoinReorderTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("DphypJoinReorder.json"));

    @Test
    void reordersLeftDeepThreeWayInnerJoin() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Catalyst.some(new LessThan(x, y)), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                Catalyst.some(new And(new GreaterThan(x, z), new LessThanOrEqual(y, z))), JoinHint$.MODULE$.NONE());
        var bc = new Join(b, c, Inner$.MODULE$, Catalyst.some(new LessThanOrEqual(y, z)), JoinHint$.MODULE$.NONE());
        var expected = new Join(a, bc, Inner$.MODULE$,
                Catalyst.some(new And(new GreaterThan(x, z), new LessThan(x, y))), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join top && top.left() == a && top.right() instanceof Join);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterTopJoinAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Catalyst.some(new LessThan(x, y)), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, LeftOuter$.MODULE$,
                Catalyst.some(new And(new GreaterThan(x, z), new LessThanOrEqual(y, z))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctiveConditionAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Catalyst.some(new LessThan(x, y)), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                Catalyst.some(new Or(new GreaterThan(x, z), new LessThanOrEqual(y, z))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesRightDeepJoinAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var bc = new Join(b, c, Inner$.MODULE$, Catalyst.some(new LessThanOrEqual(y, z)), JoinHint$.MODULE$.NONE());
        var plan = new Join(a, bc, Inner$.MODULE$,
                Catalyst.some(new And(new GreaterThan(x, z), new LessThan(x, y))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilteredInnerJoinAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(a.output()).get(0);
        var y = Catalyst.<Attribute>list(b.output()).get(0);
        var z = Catalyst.<Attribute>list(c.output()).get(0);
        var ab = new Join(a, b, Inner$.MODULE$, Catalyst.some(new LessThan(x, y)), JoinHint$.MODULE$.NONE());
        var filtered = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), ab);
        var plan = new Join(filtered, c, Inner$.MODULE$,
                Catalyst.some(new And(new GreaterThan(x, z), new LessThanOrEqual(y, z))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

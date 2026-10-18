package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinToMultiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinToMultiJoin.json"));

    @Test
    void flattensJoinWithInnerJoinIntoOneThreeWayJoin() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var z = Support.relation("z", 1, List.of(), new Random(3));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var za = Catalyst.<Attribute>list(z.output()).get(0);
        Expression fm = new EqualTo(ya, za);
        Expression c = new Or(new GreaterThan(xa, ya), new GreaterThan(za, Literal$.MODULE$.apply(2)));
        var yz = new Join(y, z, Inner$.MODULE$, Catalyst.<Expression>some(fm), JoinHint$.MODULE$.NONE());
        var plan = new Join(x, yz, Inner$.MODULE$, Catalyst.<Expression>some(c), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join top && top.left() == x && top.joinType() == Inner$.MODULE$
                && top.condition().isDefined() && top.condition().get() instanceof And
                && top.right() instanceof Join inner && inner.joinType() == Inner$.MODULE$
                && inner.condition().isEmpty() && inner.left() == y && inner.right() == z);
        var topCondition = (And) ((Join) rewritten).condition().get();
        assertEquals(new EqualTo(ya, za), topCondition.left());
        assertEquals(c, topCondition.right());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesJoinAloneWhenInnerPairIsOuterJoin() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var z = Support.relation("z", 1, List.of(), new Random(3));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var za = Catalyst.<Attribute>list(z.output()).get(0);
        Expression fm = new EqualTo(ya, za);
        Expression c = new Or(new GreaterThan(xa, ya), new GreaterThan(za, Literal$.MODULE$.apply(2)));
        var yz = new Join(y, z, LeftOuter$.MODULE$, Catalyst.<Expression>some(fm), JoinHint$.MODULE$.NONE());
        var plan = new Join(x, yz, Inner$.MODULE$, Catalyst.<Expression>some(c), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinAloneWhenAFilterSitsBetweenTheJoins() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var z = Support.relation("z", 1, List.of(), new Random(3));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var za = Catalyst.<Attribute>list(z.output()).get(0);
        Expression fm = new EqualTo(ya, za);
        Expression c = new Or(new GreaterThan(xa, ya), new GreaterThan(za, Literal$.MODULE$.apply(2)));
        var yz = new Join(y, z, Inner$.MODULE$, Catalyst.<Expression>some(Literal$.MODULE$.TrueLiteral()), JoinHint$.MODULE$.NONE());
        var plan = new Join(x, new Filter(fm, yz), Inner$.MODULE$, Catalyst.<Expression>some(c), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

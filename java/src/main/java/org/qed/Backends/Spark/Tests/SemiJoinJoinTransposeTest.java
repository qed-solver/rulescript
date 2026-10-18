package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemiJoinJoinTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SemiJoinJoinTranspose.json"));

    @Test
    void pushesSemiJoinBelowInnerJoin() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var z = Support.relation("z", 1, List.of(), new Random(3));
        var x0 = Catalyst.<Attribute>list(x.output()).get(0);
        var y0 = Catalyst.<Attribute>list(y.output()).get(0);
        var z0 = Catalyst.<Attribute>list(z.output()).get(0);
        var lower = new Join(x, y, Inner$.MODULE$, Catalyst.some(new EqualTo(x0, y0)), JoinHint$.MODULE$.NONE());
        var plan = new Join(lower, z, LeftSemi$.MODULE$, Catalyst.some(new GreaterThan(x0, z0)), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join);
        var top = (Join) rewritten;
        assertTrue(top.joinType() == Inner$.MODULE$ && top.right() == y);
        assertTrue(top.left() instanceof Join);
        var semi = (Join) top.left();
        assertTrue(semi.joinType() == LeftSemi$.MODULE$ && semi.left() == x && semi.right() == z);
        assertEquals(new EqualTo(x0, y0), top.condition().get());
        assertEquals(new GreaterThan(x0, z0), semi.condition().get());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterLowerJoinAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var z = Support.relation("z", 1, List.of(), new Random(3));
        var x0 = Catalyst.<Attribute>list(x.output()).get(0);
        var y0 = Catalyst.<Attribute>list(y.output()).get(0);
        var z0 = Catalyst.<Attribute>list(z.output()).get(0);
        var lower = new Join(x, y, LeftOuter$.MODULE$, Catalyst.some(new EqualTo(x0, y0)), JoinHint$.MODULE$.NONE());
        var plan = new Join(lower, z, LeftSemi$.MODULE$, Catalyst.some(new GreaterThan(x0, z0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSemiKeyOnLowerJoinRightSideAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var z = Support.relation("z", 1, List.of(), new Random(3));
        var x0 = Catalyst.<Attribute>list(x.output()).get(0);
        var y0 = Catalyst.<Attribute>list(y.output()).get(0);
        var z0 = Catalyst.<Attribute>list(z.output()).get(0);
        var lower = new Join(x, y, Inner$.MODULE$, Catalyst.some(new EqualTo(x0, y0)), JoinHint$.MODULE$.NONE());
        var plan = new Join(lower, z, LeftSemi$.MODULE$, Catalyst.some(new GreaterThan(y0, z0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerTopJoinAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var z = Support.relation("z", 1, List.of(), new Random(3));
        var x0 = Catalyst.<Attribute>list(x.output()).get(0);
        var y0 = Catalyst.<Attribute>list(y.output()).get(0);
        var z0 = Catalyst.<Attribute>list(z.output()).get(0);
        var lower = new Join(x, y, Inner$.MODULE$, Catalyst.some(new EqualTo(x0, y0)), JoinHint$.MODULE$.NONE());
        var plan = new Join(lower, z, Inner$.MODULE$, Catalyst.some(new GreaterThan(x0, z0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

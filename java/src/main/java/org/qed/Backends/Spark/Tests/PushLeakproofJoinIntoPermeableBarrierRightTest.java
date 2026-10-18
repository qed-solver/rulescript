package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
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

class PushLeakproofJoinIntoPermeableBarrierRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("PushLeakproofJoinIntoPermeableBarrierRight.json"));

    @Test
    void pushesJoinBelowIdentityProjection() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var barrier = new Project(Catalyst.<org.apache.spark.sql.catalyst.expressions.NamedExpression>seq(Catalyst.<Attribute>list(right.output())), right);
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(barrier.output()).get(0);
        var plan = new Join(left, barrier, Inner$.MODULE$, Option.apply(new GreaterThan(l0, r0)), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Join j
                && j.left() == left && j.right() == right);
        assertEquals(plan.output(), rewritten.output());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var barrier = new Project(Catalyst.<org.apache.spark.sql.catalyst.expressions.NamedExpression>seq(Catalyst.<Attribute>list(right.output())), right);
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(barrier.output()).get(0);
        var plan = new Join(left, barrier, LeftOuter$.MODULE$, Option.apply(new GreaterThan(l0, r0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSwappedProjectionAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var cols = Catalyst.<Attribute>list(right.output());
        var swapped = new Project(Catalyst.seq(List.of(cols.get(1), cols.get(0))), right);
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(swapped.output()).get(0);
        var plan = new Join(left, swapped, Inner$.MODULE$, Option.apply(new GreaterThan(l0, r0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithoutBarrierAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, Inner$.MODULE$, Option.apply(new GreaterThan(l0, r0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

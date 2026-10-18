package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
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

class TryDecorrelateInnerLeftJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("TryDecorrelateInnerLeftJoin.json"));

    @Test
    void pushesInnerJoinBelowLeftJoin() {
        var left = Support.relation("L", 1, List.of(0), new Random(1));
        var middle = Support.relation("IL", 1, List.of(0), new Random(2));
        var right = Support.relation("IR", 1, List.of(0), new Random(3));
        var lCol = Catalyst.<Attribute>list(left.output()).get(0);
        var ilCol = Catalyst.<Attribute>list(middle.output()).get(0);
        var irCol = Catalyst.<Attribute>list(right.output()).get(0);

        var innerLeft = new Join(middle, right, LeftOuter$.MODULE$,
                Catalyst.<Expression>some(new LessThan(ilCol, irCol)), JoinHint$.MODULE$.NONE());
        var plan = new Join(left, innerLeft, Inner$.MODULE$,
                Catalyst.<Expression>some(new EqualTo(lCol, ilCol)), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join);
        var top = (Join) rewritten;
        assertEquals(LeftOuter$.MODULE$, top.joinType());
        assertEquals(right, top.right());
        assertTrue(top.left() instanceof Join);
        var inner = (Join) top.left();
        assertEquals(Inner$.MODULE$, inner.joinType());
        assertEquals(left, inner.left());
        assertEquals(middle, inner.right());
        assertEquals(Catalyst.<Expression>some(new EqualTo(lCol, ilCol)), inner.condition());
        assertEquals(Catalyst.<Expression>some(new LessThan(ilCol, irCol)), top.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOuterLeftJoinAlone() {
        var left = Support.relation("L", 1, List.of(0), new Random(1));
        var middle = Support.relation("IL", 1, List.of(0), new Random(2));
        var right = Support.relation("IR", 1, List.of(0), new Random(3));
        var lCol = Catalyst.<Attribute>list(left.output()).get(0);
        var ilCol = Catalyst.<Attribute>list(middle.output()).get(0);
        var irCol = Catalyst.<Attribute>list(right.output()).get(0);

        var innerLeft = new Join(middle, right, LeftOuter$.MODULE$,
                Catalyst.<Expression>some(new LessThan(ilCol, irCol)), JoinHint$.MODULE$.NONE());
        var plan = new Join(left, innerLeft, LeftOuter$.MODULE$,
                Catalyst.<Expression>some(new EqualTo(lCol, ilCol)), JoinHint$.MODULE$.NONE());

        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerRightChildAlone() {
        var left = Support.relation("L", 1, List.of(0), new Random(1));
        var middle = Support.relation("IL", 1, List.of(0), new Random(2));
        var right = Support.relation("IR", 1, List.of(0), new Random(3));
        var lCol = Catalyst.<Attribute>list(left.output()).get(0);
        var ilCol = Catalyst.<Attribute>list(middle.output()).get(0);
        var irCol = Catalyst.<Attribute>list(right.output()).get(0);

        var innerChild = new Join(middle, right, Inner$.MODULE$,
                Catalyst.<Expression>some(new LessThan(ilCol, irCol)), JoinHint$.MODULE$.NONE());
        var plan = new Join(left, innerChild, Inner$.MODULE$,
                Catalyst.<Expression>some(new EqualTo(lCol, ilCol)), JoinHint$.MODULE$.NONE());

        assertEquals(plan, rule.apply(plan));
    }
}

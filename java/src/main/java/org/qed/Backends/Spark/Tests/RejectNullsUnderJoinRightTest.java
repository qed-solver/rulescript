package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RejectNullsUnderJoinRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RejectNullsUnderJoinRight.json"));

    @Test
    void pushesRightNullRejectionBelowInnerJoin() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(7));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new And(new EqualTo(l0, r0), new IsNotNull(r0));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(condition), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join);
        var join = (Join) rewritten;
        assertEquals(Inner$.MODULE$, join.joinType());
        assertEquals(left, join.left());
        assertEquals(Option.<Expression>apply(condition), join.condition());
        assertTrue(join.right() instanceof Filter filter && filter.child() == right
                && filter.condition().equals(new IsNotNull(r0)));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(7));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new And(new EqualTo(l0, r0), new IsNotNull(r0));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullRejectionOnLeftColumnAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(7));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new And(new EqualTo(l0, r0), new IsNotNull(l0));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinWithoutNullRejectionAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(7));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l0, r0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

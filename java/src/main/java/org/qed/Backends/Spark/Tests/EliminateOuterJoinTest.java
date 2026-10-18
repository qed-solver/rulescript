package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.FullOuter$;
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

class EliminateOuterJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateOuterJoin.json"));

    @Test
    void turnsFullOuterJoinIntoInnerJoinUnderNullRejectingFilter() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var a = Catalyst.<Attribute>list(left.output()).get(0);
        var b = Catalyst.<Attribute>list(right.output()).get(0);
        var on = new EqualTo(a, b);
        var cond = new And(new And(new GreaterThan(a, Literal$.MODULE$.apply(0)), new IsNotNull(a)), new IsNotNull(b));
        var join = new Join(left, right, FullOuter$.MODULE$, Catalyst.some(on), JoinHint$.MODULE$.NONE());
        var plan = new Filter(cond, join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.condition().equals(cond) && f.child() instanceof Join j
                && j.joinType() == Inner$.MODULE$ && j.left().equals(left) && j.right().equals(right)
                && j.condition().equals(Catalyst.some(on)));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(3));
        var right = Support.relation("r", 1, List.of(), new Random(4));
        var a = Catalyst.<Attribute>list(left.output()).get(0);
        var b = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new IsNotNull(a), new IsNotNull(b));
        var join = new Join(left, right, LeftOuter$.MODULE$, Catalyst.some(new EqualTo(a, b)), JoinHint$.MODULE$.NONE());
        var plan = new Filter(cond, join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFullOuterJoinAloneWhenRightSideIsNotRejected() {
        var left = Support.relation("l", 1, List.of(), new Random(5));
        var right = Support.relation("r", 1, List.of(), new Random(6));
        var a = Catalyst.<Attribute>list(left.output()).get(0);
        var b = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new GreaterThan(a, Literal$.MODULE$.apply(0)), new IsNotNull(a));
        var join = new Join(left, right, FullOuter$.MODULE$, Catalyst.some(new EqualTo(a, b)), JoinHint$.MODULE$.NONE());
        var plan = new Filter(cond, join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFullOuterJoinAloneWhenNullsAreNotRejected() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("r", 1, List.of(), new Random(8));
        var a = Catalyst.<Attribute>list(left.output()).get(0);
        var b = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new IsNull(a), new IsNull(b));
        var join = new Join(left, right, FullOuter$.MODULE$, Catalyst.some(new EqualTo(a, b)), JoinHint$.MODULE$.NONE());
        var plan = new Filter(cond, join);
        assertEquals(plan, rule.apply(plan));
    }
}

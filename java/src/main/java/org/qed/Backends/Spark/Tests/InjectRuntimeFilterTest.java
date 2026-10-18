package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InjectRuntimeFilterTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InjectRuntimeFilter.json"));

    @Test
    void injectsSemiJoinOnTheEquiKey() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var key = Catalyst.<Expression>some(new EqualTo(l, r));
        var plan = new Join(left, right, Inner$.MODULE$, key, JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(plan.output(), rewritten.output());
        var body = rewritten instanceof Project project ? project.child() : rewritten;
        var topRight = body instanceof Join top ? top.right() : null;
        assertTrue(body instanceof Join top && top.joinType().equals(Inner$.MODULE$)
                && top.left() instanceof Join semi && semi.joinType().equals(LeftSemi$.MODULE$)
                && semi.left().equals(left) && semi.right().equals(right) && semi.condition().equals(key)
                && top.right() instanceof LocalRelation
                && top.condition().equals(Catalyst.<Expression>some(new EqualTo(l,
                        Catalyst.<Attribute>list(topRight.output()).get(0)))));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftOuter$.MODULE$,
                Catalyst.<Expression>some(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonEqualityJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, Inner$.MODULE$,
                Catalyst.<Expression>some(new GreaterThan(l, r)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoKeyJoinAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var lefts = Catalyst.<Attribute>list(left.output());
        var rights = Catalyst.<Attribute>list(right.output());
        var cond = new And(new EqualTo(lefts.get(0), rights.get(0)), new EqualTo(lefts.get(1), rights.get(1)));
        var plan = new Join(left, right, Inner$.MODULE$,
                Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

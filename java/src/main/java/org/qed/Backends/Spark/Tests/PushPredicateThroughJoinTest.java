package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
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

class PushPredicateThroughJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushPredicateThroughJoin.json"));

    @Test
    void pushesSingleSideConjunctsBelowInnerJoin() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var leftPred = new GreaterThan(l, Literal$.MODULE$.apply(1));
        var rightPred = new LessThan(r, Literal$.MODULE$.apply(3));
        var common = new LessThan(l, r);
        var joinCond = new EqualTo(l, r);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(joinCond), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(leftPred, new And(rightPred, common)), join);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join);
        var out = (Join) rewritten;
        assertEquals(Inner$.MODULE$, out.joinType());
        assertTrue(out.left() instanceof Filter);
        assertTrue(out.right() instanceof Filter);
        var leftFilter = (Filter) out.left();
        var rightFilter = (Filter) out.right();
        assertEquals(leftPred, leftFilter.condition());
        assertEquals(rightPred, rightFilter.condition());
        assertEquals(Catalyst.list(left.output()), Catalyst.list(leftFilter.child().output()));
        assertEquals(Catalyst.list(right.output()), Catalyst.list(rightFilter.child().output()));
        assertEquals(List.of(joinCond, common), Catalyst.conjuncts(out.condition().get()));

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(new GreaterThan(l, Literal$.MODULE$.apply(1)),
                new And(new LessThan(r, Literal$.MODULE$.apply(3)), new LessThan(l, r))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesCrossJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(new GreaterThan(l, Literal$.MODULE$.apply(1)),
                new And(new LessThan(r, Literal$.MODULE$.apply(3)), new LessThan(l, r))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterWithoutCommonConjunctAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(new GreaterThan(l, Literal$.MODULE$.apply(1)),
                new LessThan(r, Literal$.MODULE$.apply(3))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesRightSideConjunctInLeftSlotAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(new GreaterThan(r, Literal$.MODULE$.apply(1)),
                new And(new LessThan(r, Literal$.MODULE$.apply(3)), new LessThan(l, r))), join);
        assertEquals(plan, rule.apply(plan));
    }
}

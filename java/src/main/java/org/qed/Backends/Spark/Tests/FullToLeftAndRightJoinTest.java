package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.FullOuter$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.RightOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullToLeftAndRightJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FullToLeftAndRightJoin.json"));

    @Test
    void rewritesFullEquiJoinIntoLeftJoinUnionUnmatchedRightRows() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(4));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new EqualTo(l, r);
        var plan = new Join(left, right, FullOuter$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Union, "expected a union, got " + rewritten);
        var inputs = Catalyst.<LogicalPlan>list(((Union) rewritten).children());
        assertEquals(2, inputs.size());
        var leftJoin = (Join) inputs.get(0);
        assertEquals(LeftOuter$.MODULE$, leftJoin.joinType());
        assertKeyEquality(leftJoin.condition().get());
        var filter = (Filter) inputs.get(1);
        assertTrue(filter.condition() instanceof Not, "expected NOT TRUE filter, got " + filter.condition());
        var rightJoin = (Join) filter.child();
        assertEquals(RightOuter$.MODULE$, rightJoin.joinType());
        assertKeyEquality(rightJoin.condition().get());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    private static void assertKeyEquality(Expression condition) {
        var equal = (EqualTo) condition;
        assertEquals("L_0", ((Attribute) equal.left()).name());
        assertEquals("R_0", ((Attribute) equal.right()).name());
    }

    @Test
    void leavesFullJoinWithInequalityAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(4));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, FullOuter$.MODULE$, Option.<Expression>apply(new LessThan(l, r)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(4));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFullJoinOfWiderLeftSideAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(4));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, FullOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

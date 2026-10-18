package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
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

class CommuteConstTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CommuteConst.json"));

    @Test
    void swapsEqualityOperandsOverInnerCrossJoin() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var join = new Join(left, right, Inner$.MODULE$, scala.Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var columns = Catalyst.<Attribute>list(join.output());
        var x = columns.get(0);
        var y = columns.get(1);
        var plan = new Filter(new EqualTo(x, y), join);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter);
        var filter = (Filter) rewritten;
        assertTrue(filter.child() instanceof Join);
        var childJoin = (Join) filter.child();
        assertEquals(Inner$.MODULE$, childJoin.joinType());
        assertTrue(childJoin.condition().isEmpty());
        assertTrue(filter.condition() instanceof EqualTo);
        var equal = (EqualTo) filter.condition();
        var childColumns = Catalyst.<Attribute>list(filter.child().output());
        assertEquals(columns.get(1).exprId(), ((Attribute) equal.left()).exprId());
        assertEquals(columns.get(0).exprId(), ((Attribute) equal.right()).exprId());
        assertEquals(columns.get(0).exprId(), childColumns.get(0).exprId());
        assertEquals(columns.get(1).exprId(), childColumns.get(1).exprId());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void swapsEqualityOperandsOverInnerCrossJoinWithOtherRows() {
        var left = Support.relation("L", 1, List.of(), new Random(7));
        var right = Support.relation("R", 1, List.of(), new Random(8));
        var join = new Join(left, right, Inner$.MODULE$, scala.Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new EqualTo(columns.get(0), columns.get(1)), join);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var join = new Join(left, right, LeftOuter$.MODULE$, scala.Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new EqualTo(columns.get(0), columns.get(1)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithConditionAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var leftColumns = Catalyst.<Attribute>list(left.output());
        var rightColumns = Catalyst.<Attribute>list(right.output());
        var joinCondition = new EqualTo(leftColumns.get(0), rightColumns.get(0));
        var join = new Join(left, right, Inner$.MODULE$, scala.Option.<Expression>apply(joinCondition), JoinHint$.MODULE$.NONE());
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new EqualTo(columns.get(0), columns.get(1)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesGreaterThanAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var join = new Join(left, right, Inner$.MODULE$, scala.Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new GreaterThan(columns.get(0), columns.get(1)), join);
        assertEquals(plan, rule.apply(plan));
    }
}

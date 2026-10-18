package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
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

class AggregateJoinJoinRemoveTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateJoinJoinRemove.json"));

    private static List<org.apache.spark.sql.catalyst.expressions.ExprId> ids(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output()).stream().map(Attribute::exprId).toList();
    }

    private static Aggregate distinct(List<Attribute> groups, LogicalPlan child) {
        return new Aggregate(Catalyst.<Expression>seq(groups), Catalyst.<NamedExpression>seq(groups), child, scala.Option.empty());
    }

    private static Join leftJoin(LogicalPlan left, LogicalPlan right, Expression cond) {
        return new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void removesTheMiddleLeftJoinUnderDistinct() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var aCol = Catalyst.<Attribute>list(a.output()).get(0);
        var bCol = Catalyst.<Attribute>list(b.output()).get(0);
        var cCol = Catalyst.<Attribute>list(c.output()).get(0);
        var bottom = leftJoin(a, b, new EqualTo(aCol, bCol));
        var top = leftJoin(bottom, c, new EqualTo(aCol, cCol));
        var topOut = Catalyst.<Attribute>list(top.output());
        var plan = distinct(List.of(topOut.get(0), topOut.get(2)), top);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate agg && agg.child() instanceof Join join
                && join.joinType() == LeftOuter$.MODULE$
                && join.left().output().size() == 1
                && ids(join.left()).equals(ids(a))
                && ids(join.right()).equals(ids(c)));
        assertEquals(List.of(aCol.exprId(), cCol.exprId()), ids(rewritten));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAPlanAloneWhenTheBottomJoinIsInner() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var aCol = Catalyst.<Attribute>list(a.output()).get(0);
        var bCol = Catalyst.<Attribute>list(b.output()).get(0);
        var cCol = Catalyst.<Attribute>list(c.output()).get(0);
        var bottom = new Join(a, b, Inner$.MODULE$, Catalyst.<Expression>some(new EqualTo(aCol, bCol)), JoinHint$.MODULE$.NONE());
        var top = leftJoin(bottom, c, new EqualTo(aCol, cCol));
        var topOut = Catalyst.<Attribute>list(top.output());
        var plan = distinct(List.of(topOut.get(0), topOut.get(2)), top);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAPlanAloneWhenGroupingDropsTheRightColumn() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var aCol = Catalyst.<Attribute>list(a.output()).get(0);
        var bCol = Catalyst.<Attribute>list(b.output()).get(0);
        var cCol = Catalyst.<Attribute>list(c.output()).get(0);
        var bottom = leftJoin(a, b, new EqualTo(aCol, bCol));
        var top = leftJoin(bottom, c, new EqualTo(aCol, cCol));
        var topOut = Catalyst.<Attribute>list(top.output());
        var plan = distinct(List.of(topOut.get(0)), top);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAPlanAloneWhenTheTopConditionUsesTheMiddleRelation() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var aCol = Catalyst.<Attribute>list(a.output()).get(0);
        var bCol = Catalyst.<Attribute>list(b.output()).get(0);
        var cCol = Catalyst.<Attribute>list(c.output()).get(0);
        var bottom = leftJoin(a, b, new EqualTo(aCol, bCol));
        var top = leftJoin(bottom, c, new EqualTo(bCol, cCol));
        var topOut = Catalyst.<Attribute>list(top.output());
        var plan = distinct(List.of(topOut.get(0), topOut.get(2)), top);
        assertEquals(plan, rule.apply(plan));
    }
}

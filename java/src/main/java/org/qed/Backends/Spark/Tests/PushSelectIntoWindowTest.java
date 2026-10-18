package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
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

class PushSelectIntoWindowTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushSelectIntoWindow.json"));

    private static Attribute column(LogicalPlan plan, int index) {
        return Catalyst.<Attribute>list(plan.output()).get(index);
    }

    private static Expression literal(int value) {
        return (Expression) Literal$.MODULE$.apply(value);
    }

    private static Aggregate maxPerKey(LogicalPlan child) {
        var key = column(child, 0);
        var value = column(child, 1);
        Expression max = AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        Alias w = Alias$.MODULE$.apply(max, "w", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        return new Aggregate(Catalyst.seq(List.<Expression>of(key)), Catalyst.seq(List.<NamedExpression>of(key, w)),
                child, Option.<AggregateHint>empty());
    }

    private static Join window(LogicalPlan left, LogicalPlan right, JoinType type, Attribute leftKey,
            Attribute rightKey) {
        return new Join(left, right, type, Option.<Expression>apply(new EqualTo(leftKey, rightKey)),
                JoinHint$.MODULE$.NONE());
    }

    @Test
    void pushesPartitionPredicateBelowWindow() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(7));
        var copy = Catalyst.fresh(base);
        var k = column(base, 0);
        var v = column(base, 1);
        var perPart = maxPerKey(copy);
        var join = window(base, perPart, Inner$.MODULE$, k, column(copy, 0));
        var w = column(join, 3);
        var plan = new Filter(new And(new GreaterThan(k, literal(1)), new LessThan(v, w)), join);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter, "result is not a Filter");
        var top = (Filter) rewritten;
        assertTrue(top.child() instanceof Join, "Q is not kept above the window");
        var newJoin = (Join) top.child();
        assertTrue(newJoin.left() instanceof Filter, "P is not applied to the window input");
        assertTrue(newJoin.right() instanceof Aggregate, "window aggregate is missing");
        var rightAggregate = (Aggregate) newJoin.right();
        assertTrue(rightAggregate.child() instanceof Filter, "P is not applied below the per-partition aggregate");
        assertEquals(new GreaterThan(k, literal(1)), ((Filter) newJoin.left()).condition());
        assertEquals(v, ((LessThan) top.condition()).left());
        assertEquals(4, Catalyst.list(rewritten.output()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOverWindowWithoutPartitionPredicateAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(7));
        var copy = Catalyst.fresh(base);
        var k = column(base, 0);
        var v = column(base, 1);
        var join = window(base, maxPerKey(copy), Inner$.MODULE$, k, column(copy, 0));
        var w = column(join, 3);
        var plan = new Filter(new And(new GreaterThan(v, literal(1)), new LessThan(v, w)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftOuterWindowAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(8));
        var copy = Catalyst.fresh(base);
        var k = column(base, 0);
        var v = column(base, 1);
        var join = window(base, maxPerKey(copy), LeftOuter$.MODULE$, k, column(copy, 0));
        var w = column(join, 3);
        var plan = new Filter(new And(new GreaterThan(k, literal(1)), new LessThan(v, w)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesWindowJoinedOnNonPartitionColumnAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(9));
        var copy = Catalyst.fresh(base);
        var k = column(base, 0);
        var v = column(base, 1);
        var join = window(base, maxPerKey(copy), Inner$.MODULE$, v, column(copy, 0));
        var w = column(join, 3);
        var plan = new Filter(new And(new GreaterThan(k, literal(1)), new LessThan(v, w)), join);
        assertEquals(plan, rule.apply(plan));
    }
}

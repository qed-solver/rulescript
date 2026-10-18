package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.InternalRow;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryDecorrelateGroupByTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryDecorrelateGroupBy.json"));

    private static LogicalPlan distinctKeys(String name) {
        var attr = Support.attribute(name, false);
        var rows = new ArrayList<InternalRow>();
        for (int v = 0; v < 4; v++) {
            rows.add(new GenericInternalRow(new Object[] {v}));
        }
        var source = new LocalRelation(Catalyst.seq(List.of(attr)), Catalyst.seq(rows), false, Match.noStream());
        return new Aggregate(Catalyst.seq(List.<Expression>of(attr)), Catalyst.seq(List.<NamedExpression>of(attr)),
                source, Option.<AggregateHint>empty());
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression maxOf(Attribute a) {
        return AggregateExpression$.MODULE$.apply(new Max(a), Complete$.MODULE$, false, Option.<Expression>empty(),
                NamedExpression$.MODULE$.newExprId());
    }

    private static LogicalPlan groupByMax(LogicalPlan child, Attribute g, Attribute a) {
        var f = alias(maxOf(a), "f");
        var items = List.<NamedExpression>of(g, f);
        return new Aggregate(Catalyst.seq(List.<Expression>of(g)), Catalyst.seq(items), child,
                Option.<AggregateHint>empty());
    }

    private static Join innerCross(LogicalPlan left, LogicalPlan right) {
        return new Join(left, right, Match.joinType("inner"), Option.<Expression>apply(Literal$.MODULE$.TrueLiteral()),
                JoinHint$.MODULE$.NONE());
    }

    @Test
    void decorrelatesGroupByUnderCrossJoin() {
        var left = distinctKeys("l");
        var r = Support.relation("r", 2, List.of(0, 1), new Random(7));
        var rAttrs = Catalyst.<Attribute>list(r.output());
        var grouped = groupByMax(r, rAttrs.get(0), rAttrs.get(1));
        var join = innerCross(left, grouped);
        var f = Catalyst.<Attribute>list(join.output()).get(2);
        var plan = new Filter(new GreaterThan(f, Literal$.MODULE$.apply(1)), join);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter, "top node must stay a Filter");
        var agg = ((Filter) rewritten).child();
        assertTrue(agg instanceof Aggregate, "Filter must sit on an Aggregate");
        assertEquals(2, ((Aggregate) agg).groupingExpressions().size());
        assertTrue(((Aggregate) agg).child() instanceof Join, "Aggregate must sit on the cross join");
        assertEquals(3, rewritten.output().size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = distinctKeys("l");
        var r = Support.relation("r", 2, List.of(0, 1), new Random(7));
        var rAttrs = Catalyst.<Attribute>list(r.output());
        var grouped = groupByMax(r, rAttrs.get(0), rAttrs.get(1));
        var join = new Join(left, grouped, Match.joinType("left"),
                Option.<Expression>apply(Literal$.MODULE$.TrueLiteral()), JoinHint$.MODULE$.NONE());
        var f = Catalyst.<Attribute>list(join.output()).get(2);
        var plan = new Filter(new GreaterThan(f, Literal$.MODULE$.apply(1)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesGroupByWithTwoAggregatesAlone() {
        var left = distinctKeys("l");
        var r = Support.relation("r", 2, List.of(0, 1), new Random(7));
        var rAttrs = Catalyst.<Attribute>list(r.output());
        var g = rAttrs.get(0);
        var a = rAttrs.get(1);
        var items = List.<NamedExpression>of(g, alias(maxOf(a), "f1"), alias(maxOf(a), "f2"));
        var grouped = new Aggregate(Catalyst.seq(List.<Expression>of(g)), Catalyst.seq(items), r,
                Option.<AggregateHint>empty());
        var join = innerCross(left, grouped);
        var f = Catalyst.<Attribute>list(join.output()).get(2);
        var plan = new Filter(new GreaterThan(f, Literal$.MODULE$.apply(1)), join);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LateralJoin;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryDecorrelateSemiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryDecorrelateSemiJoin.json"));

    private static LocalRelation table(Attribute column, List<Integer> values) {
        var rows = new ArrayList<InternalRow>();
        for (int v : values) {
            rows.add(new GenericInternalRow(new Object[] {v}));
        }
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(column)), Catalyst.<InternalRow>seq(rows),
                false, Match.noStream());
    }

    private static LogicalPlan keyedTable(Attribute column, List<Integer> values) {
        var relation = table(column, values);
        var key = Catalyst.<Attribute>list(relation.output()).get(0);
        return new Aggregate(Catalyst.<Expression>seq(List.<Expression>of(key)), Catalyst.<NamedExpression>seq(List.<NamedExpression>of(key)),
                relation, Option.<AggregateHint>empty());
    }

    @Test
    void semiJoinOnKeyBecomesGroupByOverInnerJoin() {
        var left = keyedTable(Support.attribute("l0", false), List.of(0, 1, 2));
        var right = table(Support.attribute("r0", false), List.of(1, 1, 2));
        var lkey = Catalyst.<Attribute>list(left.output()).get(0);
        var rkey = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftSemi$.MODULE$,
                Option.<Expression>apply(new EqualTo(lkey, rkey)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var aggregate = (Aggregate) rewritten;
        assertTrue(aggregate.child() instanceof LateralJoin);
        var inner = (LateralJoin) aggregate.child();
        assertEquals(Inner$.MODULE$, inner.joinType());
        assertEquals(left, inner.left());
        assertTrue(inner.right().plan() instanceof Filter);
        assertEquals(right, ((Filter) inner.right().plan()).child());
        assertEquals(List.of(Catalyst.<Attribute>list(inner.output()).get(0)),
                Catalyst.list(aggregate.groupingExpressions()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void antiJoinIsLeftAlone() {
        var left = keyedTable(Support.attribute("l0", false), List.of(0, 1, 2));
        var right = table(Support.attribute("r0", false), List.of(1, 1, 2));
        var lkey = Catalyst.<Attribute>list(left.output()).get(0);
        var rkey = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftAnti$.MODULE$,
                Option.<Expression>apply(new EqualTo(lkey, rkey)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void semiJoinWithoutKeyIsLeftAlone() {
        var left = table(Support.attribute("l0", false), List.of(0, 0, 1));
        var right = table(Support.attribute("r0", false), List.of(1, 1, 2));
        var lkey = Catalyst.<Attribute>list(left.output()).get(0);
        var rkey = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, LeftSemi$.MODULE$,
                Option.<Expression>apply(new EqualTo(lkey, rkey)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

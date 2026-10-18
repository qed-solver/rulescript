package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import scala.Option;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldGroupByAndWindowTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldGroupByAndWindow.json"));

    private static List<Attribute> attrs(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output());
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression max(Attribute a) {
        return AggregateExpression$.MODULE$.apply(new Max(a), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
    }

    private static LogicalPlan window(LogicalPlan input) {
        var a = attrs(input);
        return new Aggregate(Catalyst.seq(List.<Expression>of(a.get(0))),
                Catalyst.seq(List.<NamedExpression>of(a.get(0), alias(max(a.get(1)), "w"))), input,
                Option.<AggregateHint>empty());
    }

    private static LogicalPlan groupMax(LogicalPlan input) {
        var a = attrs(input);
        return new Aggregate(Catalyst.seq(List.<Expression>of(a.get(0))),
                Catalyst.seq(List.<NamedExpression>of(a.get(0), alias(max(a.get(1)), "a"))), input,
                Option.<AggregateHint>empty());
    }

    private static LogicalPlan joined(LogicalPlan left, LogicalPlan right, org.apache.spark.sql.catalyst.plans.JoinType kind,
            Expression cond) {
        return new Join(left, right, kind, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void foldsGroupByOverPartitionJoinIntoGroupByOnInput() {
        var input = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var win = window((LogicalPlan) input.newInstance());
        var k = attrs(input).get(0);
        var kWindow = attrs(win).get(0);
        var plan = groupMax(joined(input, win, Inner$.MODULE$, new EqualTo(k, kWindow)));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate agg && agg.child() instanceof LocalRelation);
        assertEquals(attrs(plan).size(), attrs(rewritten).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var input = Support.relation("t", 2, List.of(0, 1), new Random(2));
        var win = window((LogicalPlan) input.newInstance());
        var k = attrs(input).get(0);
        var kWindow = attrs(win).get(0);
        var plan = groupMax(joined(input, win, LeftOuter$.MODULE$, new EqualTo(k, kWindow)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinOnNonPartitionColumnAlone() {
        var input = Support.relation("t", 2, List.of(0, 1), new Random(3));
        var win = window((LogicalPlan) input.newInstance());
        var v = attrs(input).get(1);
        var kWindow = attrs(win).get(0);
        var plan = groupMax(joined(input, win, Inner$.MODULE$, new EqualTo(v, kWindow)));
        assertEquals(plan, rule.apply(plan));
    }
}

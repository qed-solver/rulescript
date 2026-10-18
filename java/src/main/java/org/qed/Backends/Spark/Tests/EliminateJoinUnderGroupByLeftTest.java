package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.RightOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateJoinUnderGroupByLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateJoinUnderGroupByLeft.json"));

    @Test
    void dropsRightSideOfLeftJoinUnderGroupBy() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(lx, rx)), JoinHint$.MODULE$.NONE());
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(lx)), Catalyst.<NamedExpression>seq(List.of(lx)), join,
                Option.<AggregateHint>empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate agg && agg.child().equals(left));
        assertEquals(List.of(lx.exprId()), Catalyst.<Attribute>list(rewritten.output()).stream().map(x -> x.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesRightJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, RightOuter$.MODULE$, Option.<Expression>apply(new EqualTo(lx, rx)), JoinHint$.MODULE$.NONE());
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(lx)), Catalyst.<NamedExpression>seq(List.of(lx)), join,
                Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesGroupingOnRightSideAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(lx, rx)), JoinHint$.MODULE$.NONE());
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(rx)), Catalyst.<NamedExpression>seq(List.of(rx)), join,
                Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesWiderLeftInputAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(lx, rx)), JoinHint$.MODULE$.NONE());
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(lx)), Catalyst.<NamedExpression>seq(List.of(lx)), join,
                Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }
}

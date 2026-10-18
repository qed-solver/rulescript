package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateToSemiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateToSemiJoin.json"));

    private static LogicalPlan groupedJoin(LocalRelation left, LocalRelation right, JoinType type) {
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var distinctRight = new Aggregate(Catalyst.<Expression>seq(List.of(r0)),
                Catalyst.<NamedExpression>seq(List.of(r0)), right, Option.<AggregateHint>empty());
        var join = new Join(left, distinctRight, type, Option.<Expression>apply(new EqualTo(l0, r0)),
                JoinHint$.MODULE$.NONE());
        return new Aggregate(Catalyst.<Expression>seq(List.of(l0)), Catalyst.<NamedExpression>seq(List.of(l0)),
                join, Option.<AggregateHint>empty());
    }

    private static LogicalPlan semiJoinGrouped(LocalRelation left, LocalRelation right) {
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftSemi$.MODULE$, Option.<Expression>apply(new EqualTo(l0, r0)),
                JoinHint$.MODULE$.NONE());
        return new Aggregate(Catalyst.<Expression>seq(List.of(l0)), Catalyst.<NamedExpression>seq(List.of(l0)),
                join, Option.<AggregateHint>empty());
    }

    @Test
    void rewritesGroupedInnerJoinOnDistinctRightIntoSemiJoin() {
        var left = Support.relation("L", 1, List.of(0), new Random(1));
        var right = Support.relation("R", 1, List.of(0), new Random(2));
        var plan = groupedJoin(left, right, Inner$.MODULE$);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate a && a.child() instanceof Join j
                && j.joinType().equals(LeftSemi$.MODULE$) && j.left().equals(left) && j.right().equals(right));
        assertEquals(semiJoinGrouped(left, right), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void rewritesWhenKeysAreNullable() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(4));
        var plan = groupedJoin(left, right, Inner$.MODULE$);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate a && a.child() instanceof Join j
                && j.joinType().equals(LeftSemi$.MODULE$));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 1, List.of(0), new Random(5));
        var right = Support.relation("R", 1, List.of(0), new Random(6));
        var plan = groupedJoin(left, right, LeftOuter$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }
}

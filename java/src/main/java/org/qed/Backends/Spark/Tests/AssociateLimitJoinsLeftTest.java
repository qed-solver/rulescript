package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssociateLimitJoinsLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AssociateLimitJoinsLeft.json"));

    private static Attribute col(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output()).get(0);
    }

    private static Join join(LogicalPlan left, LogicalPlan right, org.apache.spark.sql.catalyst.plans.JoinType type, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void reassociatesLeftJoinUnderInnerJoin() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var a0 = col(a);
        var b0 = col(b);
        var c0 = col(c);
        var ab = join(a, b, LeftOuter$.MODULE$, new EqualTo(a0, b0));
        var plan = join(ab, c, Inner$.MODULE$, new LessThan(a0, c0));
        var expectedOutput = Catalyst.list(plan.output()).stream().map(x -> x.exprId()).toList();

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project, "result is not a projection: " + rewritten);
        var project = (Project) rewritten;
        assertEquals(expectedOutput, Catalyst.list(project.output()).stream().map(x -> x.exprId()).toList());
        assertTrue(project.child() instanceof Join, "projection child is not a join: " + project.child());
        var top = (Join) project.child();
        assertEquals(LeftOuter$.MODULE$, top.joinType());
        assertTrue(top.left() instanceof Join, "left input of the outer join is not a join");
        var inner = (Join) top.left();
        assertEquals(Inner$.MODULE$, inner.joinType());
        assertEquals(List.of(a0.exprId()), Catalyst.list(inner.left().output()).stream().map(x -> x.exprId()).toList());
        assertEquals(List.of(c0.exprId()), Catalyst.list(inner.right().output()).stream().map(x -> x.exprId()).toList());
        assertEquals(List.of(b0.exprId()), Catalyst.list(top.right().output()).stream().map(x -> x.exprId()).toList());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesInnerJoinUnderInnerJoinAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var a0 = col(a);
        var b0 = col(b);
        var c0 = col(c);
        var ab = join(a, b, Inner$.MODULE$, new EqualTo(a0, b0));
        var plan = join(ab, c, Inner$.MODULE$, new LessThan(a0, c0));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOuterCorrelationWithLeftSideAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var a0 = col(a);
        var b0 = col(b);
        var c0 = col(c);
        var ab = join(a, b, LeftOuter$.MODULE$, new EqualTo(a0, b0));
        var plan = join(ab, c, Inner$.MODULE$, new LessThan(b0, c0));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOuterLeftJoinAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var a0 = col(a);
        var b0 = col(b);
        var c0 = col(c);
        var ab = join(a, b, LeftOuter$.MODULE$, new EqualTo(a0, b0));
        var plan = join(ab, c, LeftOuter$.MODULE$, new LessThan(a0, c0));
        assertEquals(plan, rule.apply(plan));
    }
}

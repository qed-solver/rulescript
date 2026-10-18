package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizeSubqueriesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("OptimizeSubqueries.json"));

    @Test
    void prunesSemiJoinSubqueryToTheColumnsOfTheCondition() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 3, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var rightCols = Catalyst.<Attribute>list(right.output());
        var cond = new And(new EqualTo(l0, rightCols.get(0)), new GreaterThan(l0, rightCols.get(2)));
        var plan = new Join(left, right, LeftSemi$.MODULE$, scala.Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond),
                JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var join = (Join) rewritten;
        assertSame(LeftSemi$.MODULE$, join.joinType());
        assertSame(left, join.left());
        assertTrue(join.right() instanceof Project);
        var project = (Project) join.right();
        assertSame(right, project.child());
        assertEquals(List.of("r_0", "r_2"), Catalyst.list(project.output()).stream().map(Attribute::name).toList());
        assertEquals(cond, join.condition().get());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void prunesSemiJoinSubqueryWhenConditionUsesOnlyTheLastColumn() {
        var left = Support.relation("l", 1, List.of(), new Random(3));
        var right = Support.relation("r", 3, List.of(), new Random(4));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var rightCols = Catalyst.<Attribute>list(right.output());
        var cond = new GreaterThan(l0, rightCols.get(2));
        var plan = new Join(left, right, LeftSemi$.MODULE$, scala.Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond),
                JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var join = (Join) rewritten;
        assertTrue(join.right() instanceof Project);
        assertEquals(List.of("r_0", "r_2"), Catalyst.list(join.right().output()).stream().map(Attribute::name).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void prunesSemiJoinSubqueryForDisjunctiveCondition() {
        var left = Support.relation("l", 1, List.of(), new Random(9));
        var right = Support.relation("r", 3, List.of(), new Random(10));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var rightCols = Catalyst.<Attribute>list(right.output());
        var cond = new Or(new EqualTo(l0, rightCols.get(0)), new GreaterThan(l0, rightCols.get(2)));
        var plan = new Join(left, right, LeftSemi$.MODULE$, scala.Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond),
                JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var join = (Join) rewritten;
        assertTrue(join.right() instanceof Project);
        assertEquals(List.of("r_0", "r_2"), Catalyst.list(join.right().output()).stream().map(Attribute::name).toList());
        assertEquals(cond, join.condition().get());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesInnerJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(5));
        var right = Support.relation("r", 3, List.of(), new Random(6));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var rightCols = Catalyst.<Attribute>list(right.output());
        var cond = new And(new EqualTo(l0, rightCols.get(0)), new GreaterThan(l0, rightCols.get(2)));
        var plan = new Join(left, right, Inner$.MODULE$, scala.Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNarrowSubquerySemiJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("r", 2, List.of(), new Random(8));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var rightCols = Catalyst.<Attribute>list(right.output());
        var cond = new EqualTo(l0, rightCols.get(1));
        var plan = new Join(left, right, LeftSemi$.MODULE$, scala.Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

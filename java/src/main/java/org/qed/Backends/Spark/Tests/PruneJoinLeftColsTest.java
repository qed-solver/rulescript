package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
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

class PruneJoinLeftColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneJoinLeftCols.json"));

    private static Alias alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression add(Expression left, Expression right) {
        return Add$.MODULE$.apply(left, right, Add$.MODULE$.apply$default$3());
    }

    @Test
    void prunesTheUnusedLeftColumnBelowAnInnerJoin() {
        var left = Support.relation("L", 2, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(4));
        var leftOut = Catalyst.<Attribute>list(left.output());
        var rightOut = Catalyst.<Attribute>list(right.output());
        var join = new Join(left, right, Inner$.MODULE$,
                Option.<Expression>apply(new EqualTo(leftOut.get(0), rightOut.get(0))), JoinHint$.MODULE$.NONE());
        var joinOut = Catalyst.<Attribute>list(join.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(
                alias(add(joinOut.get(0), joinOut.get(2)), "g"))), join);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        var topItems = Catalyst.<NamedExpression>list(top.projectList());
        assertEquals(1, topItems.size());
        assertTrue(top.child() instanceof Join);
        var j = (Join) top.child();
        assertEquals(Inner$.MODULE$, j.joinType());
        assertEquals(right, j.right());
        assertTrue(j.left() instanceof Project);
        var pruned = (Project) j.left();
        assertEquals(left, pruned.child());
        var prunedItems = Catalyst.<NamedExpression>list(pruned.projectList());
        assertEquals(1, prunedItems.size());
        assertEquals(leftOut.get(0).exprId(), prunedItems.get(0).exprId());
        var rewrittenJoinOut = Catalyst.<Attribute>list(j.output());
        assertEquals(new EqualTo(rewrittenJoinOut.get(0), rewrittenJoinOut.get(1)), j.condition().get());
        assertEquals(add(rewrittenJoinOut.get(0), rewrittenJoinOut.get(1)), ((Alias) topItems.get(0)).child());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(4));
        var leftOut = Catalyst.<Attribute>list(left.output());
        var rightOut = Catalyst.<Attribute>list(right.output());
        var join = new Join(left, right, LeftOuter$.MODULE$,
                Option.<Expression>apply(new EqualTo(leftOut.get(0), rightOut.get(0))), JoinHint$.MODULE$.NONE());
        var joinOut = Catalyst.<Attribute>list(join.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(
                alias(add(joinOut.get(0), joinOut.get(2)), "g"))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAJoinAloneWhenTheOuterProjectionNeedsTheDroppedColumn() {
        var left = Support.relation("L", 2, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(4));
        var leftOut = Catalyst.<Attribute>list(left.output());
        var rightOut = Catalyst.<Attribute>list(right.output());
        var join = new Join(left, right, Inner$.MODULE$,
                Option.<Expression>apply(new EqualTo(leftOut.get(0), rightOut.get(0))), JoinHint$.MODULE$.NONE());
        var joinOut = Catalyst.<Attribute>list(join.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(
                alias(add(joinOut.get(1), joinOut.get(2)), "g"))), join);
        assertEquals(plan, rule.apply(plan));
    }
}

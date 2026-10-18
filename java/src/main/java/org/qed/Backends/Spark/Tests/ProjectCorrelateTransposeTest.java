package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
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

class ProjectCorrelateTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectCorrelateTranspose.json"));

    @Test
    void projectsOneColumnFromEachSideOfACrossJoin() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var leftCols = Catalyst.<Attribute>list(left.output());
        var rightCols = Catalyst.<Attribute>list(right.output());
        var join = new Join(left, right, Inner$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>empty(),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(leftCols.get(0), rightCols.get(0))), join);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        assertEquals(2, Catalyst.list(top.projectList()).size());
        assertTrue(top.child() instanceof Join);
        var newJoin = (Join) top.child();
        assertTrue(newJoin.joinType() == Inner$.MODULE$);
        assertTrue(newJoin.condition().isEmpty());
        assertTrue(newJoin.left() instanceof Project);
        assertTrue(newJoin.right() instanceof Project);
        assertEquals(1, Catalyst.list(((Project) newJoin.left()).projectList()).size());
        assertEquals(1, Catalyst.list(((Project) newJoin.right()).projectList()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var leftCols = Catalyst.<Attribute>list(left.output());
        var rightCols = Catalyst.<Attribute>list(right.output());
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>empty(),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(leftCols.get(0), rightCols.get(0))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAJoinWithAConditionAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var leftCols = Catalyst.<Attribute>list(left.output());
        var rightCols = Catalyst.<Attribute>list(right.output());
        var cond = new EqualTo(leftCols.get(1), rightCols.get(1));
        var join = new Join(left, right, Inner$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(leftCols.get(0), rightCols.get(0))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectionOfTheLeftSideAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var leftCols = Catalyst.<Attribute>list(left.output());
        var join = new Join(left, right, Inner$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>empty(),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(leftCols.get(0), leftCols.get(1))), join);
        assertEquals(plan, rule.apply(plan));
    }
}

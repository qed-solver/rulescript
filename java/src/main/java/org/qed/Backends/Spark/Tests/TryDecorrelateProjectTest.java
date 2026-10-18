package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryDecorrelateProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryDecorrelateProject.json"));

    @Test
    void pushesSwappedRightProjectIntoJoinCondition() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var leftCols = Catalyst.<Attribute>list(left.output());
        var rightCols = Catalyst.<Attribute>list(right.output());
        var swapped = new Project(Catalyst.<NamedExpression>seq(List.of(rightCols.get(1), rightCols.get(0))), right);
        var swappedCols = Catalyst.<Attribute>list(swapped.output());
        var cond = new GreaterThan(leftCols.get(0), swappedCols.get(0));
        var plan = new Join(left, swapped, Inner$.MODULE$, Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertInstanceOf(Filter.class, rewritten);
        var filter = (Filter) rewritten;
        assertEquals(cond, filter.condition());
        assertInstanceOf(Project.class, filter.child());
        var project = (Project) filter.child();
        assertInstanceOf(Join.class, project.child());
        var inner = (Join) project.child();
        assertEquals(Inner$.MODULE$, inner.joinType());
        assertTrue(inner.condition().isEmpty());
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var rightCols = Catalyst.<Attribute>list(right.output());
        var swapped = new Project(Catalyst.<NamedExpression>seq(List.of(rightCols.get(1), rightCols.get(0))), right);
        var leftCols = Catalyst.<Attribute>list(left.output());
        var swappedCols = Catalyst.<Attribute>list(swapped.output());
        var cond = new GreaterThan(leftCols.get(0), swappedCols.get(0));
        var plan = new Join(left, swapped, LeftOuter$.MODULE$, Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNarrowRightProjectAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var rightCols = Catalyst.<Attribute>list(right.output());
        var narrow = new Project(Catalyst.<NamedExpression>seq(List.of(rightCols.get(1))), right);
        var leftCols = Catalyst.<Attribute>list(left.output());
        var narrowCols = Catalyst.<Attribute>list(narrow.output());
        var cond = new GreaterThan(leftCols.get(0), narrowCols.get(0));
        var plan = new Join(left, narrow, Inner$.MODULE$, Catalyst.<Expression>some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

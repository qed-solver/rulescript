package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoistJoinProjectLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("HoistJoinProjectLeft.json"));

    private static Expression condition(Attribute a, Attribute b, Attribute c) {
        return new Or(new GreaterThan(a, b), new EqualTo(b, c));
    }

    @Test
    void hoistsSwappedLeftProjectAboveInnerJoin() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var leftCols = Catalyst.<Attribute>list(left.output());
        var rightCols = Catalyst.<Attribute>list(right.output());
        var swapped = new Project(Catalyst.<NamedExpression>seq(List.of(leftCols.get(1), leftCols.get(0))), left);
        var swappedCols = Catalyst.<Attribute>list(swapped.output());
        var plan = new Join(swapped, right, Inner$.MODULE$,
                Catalyst.some(condition(swappedCols.get(0), swappedCols.get(1), rightCols.get(0))), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project, "result should be a Project on top");
        var top = (Project) rewritten;
        assertTrue(top.child() instanceof Join, "Project should sit on a Join");
        var join = (Join) top.child();
        assertTrue(join.left() == left && join.right() == right, "join should take the raw inputs");
        assertEquals(plan.output(), rewritten.output());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var leftCols = Catalyst.<Attribute>list(left.output());
        var rightCols = Catalyst.<Attribute>list(right.output());
        var swapped = new Project(Catalyst.<NamedExpression>seq(List.of(leftCols.get(1), leftCols.get(0))), left);
        var swappedCols = Catalyst.<Attribute>list(swapped.output());
        var plan = new Join(swapped, right, LeftOuter$.MODULE$,
                Catalyst.some(condition(swappedCols.get(0), swappedCols.get(1), rightCols.get(0))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithoutLeftProjectAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var leftCols = Catalyst.<Attribute>list(left.output());
        var rightCols = Catalyst.<Attribute>list(right.output());
        var plan = new Join(left, right, Inner$.MODULE$,
                Catalyst.some(condition(leftCols.get(1), leftCols.get(0), rightCols.get(0))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesThreeColumnLeftInputAlone() {
        var left = Support.relation("l", 3, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var leftCols = Catalyst.<Attribute>list(left.output());
        var rightCols = Catalyst.<Attribute>list(right.output());
        var swapped = new Project(Catalyst.<NamedExpression>seq(List.of(leftCols.get(1), leftCols.get(0))), left);
        var swappedCols = Catalyst.<Attribute>list(swapped.output());
        var plan = new Join(swapped, right, Inner$.MODULE$,
                Catalyst.some(condition(swappedCols.get(0), swappedCols.get(1), rightCols.get(0))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDuplicatingLeftProjectAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var leftCols = Catalyst.<Attribute>list(left.output());
        var rightCols = Catalyst.<Attribute>list(right.output());
        var duplicated = new Project(Catalyst.<NamedExpression>seq(List.of(leftCols.get(1), leftCols.get(1))), left);
        var duplicatedCols = Catalyst.<Attribute>list(duplicated.output());
        var plan = new Join(duplicated, right, Inner$.MODULE$,
                Catalyst.some(condition(duplicatedCols.get(0), duplicatedCols.get(1), rightCols.get(0))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

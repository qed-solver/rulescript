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
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoistJoinProjectRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("HoistJoinProjectRight.json"));

    private static Expression on(Attribute l0, Attribute b, Attribute c) {
        return new Or(new GreaterThan(b, l0), new EqualTo(c, l0));
    }

    @Test
    void hoistsTheRightProjectAboveALeftJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var r1 = Catalyst.<Attribute>list(right.output()).get(1);
        var swapped = new Project(Catalyst.<NamedExpression>seq(List.of(r1, r0)), right);
        var condition = on(l0, r1, r0);
        var plan = new Join(left, swapped, LeftOuter$.MODULE$, Option.apply(condition), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project, "result is not a Project: " + rewritten);
        var top = (Project) rewritten;
        assertTrue(top.child() instanceof Join, "Project child is not a Join: " + rewritten);
        var join = (Join) top.child();
        assertEquals(LeftOuter$.MODULE$, join.joinType());
        assertTrue(join.left() == left, "left input changed");
        assertTrue(join.right() == right, "right input is not the unprojected relation");
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        assertEquals(List.of(l0.exprId(), r1.exprId(), r0.exprId()),
                Catalyst.list(top.projectList()).stream().map(a -> ((NamedExpression) a).exprId()).toList());
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnInnerJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var r1 = Catalyst.<Attribute>list(right.output()).get(1);
        var swapped = new Project(Catalyst.<NamedExpression>seq(List.of(r1, r0)), right);
        var plan = new Join(left, swapped, Inner$.MODULE$, Option.apply(on(l0, r1, r0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAThreeColumnRightInputAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 3, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var r1 = Catalyst.<Attribute>list(right.output()).get(1);
        var swapped = new Project(Catalyst.<NamedExpression>seq(List.of(r1, r0)), right);
        var plan = new Join(left, swapped, LeftOuter$.MODULE$, Option.apply(on(l0, r1, r0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectWithoutAPermutationAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var r1 = Catalyst.<Attribute>list(right.output()).get(1);
        var duplicated = new Project(Catalyst.<NamedExpression>seq(List.of(r1, r1)), right);
        var plan = new Join(left, duplicated, LeftOuter$.MODULE$, Option.apply(on(l0, r1, r0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

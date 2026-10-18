package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
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

class PushLeakproofJoinIntoPermeableBarrierLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("PushLeakproofJoinIntoPermeableBarrierLeft.json"));

    @Test
    void pushesJoinBelowBarrierOnLeft() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var leftAttrs = Catalyst.<Attribute>list(left.output());
        var rightAttrs = Catalyst.<Attribute>list(right.output());
        var barrier = new Project(Catalyst.<NamedExpression>seq(leftAttrs), left);
        var condition = new GreaterThan(leftAttrs.get(0), rightAttrs.get(0));
        var plan = new Join(barrier, right, Inner$.MODULE$, Catalyst.<Expression>some(condition), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        assertTrue(top.child() instanceof Join);
        var join = (Join) top.child();
        assertEquals(left, join.left());
        assertEquals(right, join.right());
        assertEquals(Inner$.MODULE$, join.joinType());
        assertEquals(Catalyst.<Expression>some(condition), join.condition());
        assertEquals(Catalyst.list(join.output()), Catalyst.list(top.projectList()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAboveBarrierAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(3));
        var right = Support.relation("r", 2, List.of(), new Random(4));
        var leftAttrs = Catalyst.<Attribute>list(left.output());
        var rightAttrs = Catalyst.<Attribute>list(right.output());
        var barrier = new Project(Catalyst.<NamedExpression>seq(leftAttrs), left);
        var condition = new GreaterThan(leftAttrs.get(0), rightAttrs.get(0));
        var plan = new Join(barrier, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinWithoutBarrierAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(5));
        var right = Support.relation("r", 2, List.of(), new Random(6));
        var leftAttrs = Catalyst.<Attribute>list(left.output());
        var rightAttrs = Catalyst.<Attribute>list(right.output());
        var condition = new GreaterThan(leftAttrs.get(0), rightAttrs.get(0));
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.<Expression>some(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

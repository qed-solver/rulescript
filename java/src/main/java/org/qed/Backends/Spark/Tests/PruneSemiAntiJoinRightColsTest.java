package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneSemiAntiJoinRightColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneSemiAntiJoinRightCols.json"));

    @Test
    void prunesUnusedRightColumnOfAntiJoin() {
        var left = Support.relation("l", 1, List.of(0), new Random(7));
        var right = Support.relation("r", 2, List.of(), new Random(8));
        var lKey = Catalyst.<Attribute>list(left.output()).get(0);
        var rKey = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = Catalyst.<Expression>some(new EqualTo(rKey, lKey));
        var plan = new Join(left, right, LeftAnti$.MODULE$, cond, JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.joinType().equals(LeftAnti$.MODULE$));
        var join = (Join) rewritten;
        assertEquals(left, join.left());
        assertTrue(join.right() instanceof Project);
        var project = (Project) join.right();
        assertEquals(right, project.child());
        assertEquals(List.of(rKey), Catalyst.<NamedExpression>list(project.projectList()));
        assertEquals(Catalyst.<Attribute>list(left.output()), Catalyst.<Attribute>list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void prunesUnusedRightColumnOfAntiJoinWithOtherRows() {
        var left = Support.relation("l", 1, List.of(0), new Random(21));
        var right = Support.relation("r", 2, List.of(), new Random(22));
        var lKey = Catalyst.<Attribute>list(left.output()).get(0);
        var rKey = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = Catalyst.<Expression>some(new EqualTo(rKey, lKey));
        var plan = new Join(left, right, LeftAnti$.MODULE$, cond, JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesSemiJoinAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(7));
        var right = Support.relation("r", 2, List.of(), new Random(8));
        var lKey = Catalyst.<Attribute>list(left.output()).get(0);
        var rKey = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = Catalyst.<Expression>some(new EqualTo(rKey, lKey));
        var plan = new Join(left, right, LeftSemi$.MODULE$, cond, JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesConditionOnPrunedColumnAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(7));
        var right = Support.relation("r", 2, List.of(), new Random(8));
        var lKey = Catalyst.<Attribute>list(left.output()).get(0);
        var prunedKey = Catalyst.<Attribute>list(right.output()).get(1);
        var cond = Catalyst.<Expression>some(new EqualTo(prunedKey, lKey));
        var plan = new Join(left, right, LeftAnti$.MODULE$, cond, JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesThreeColumnRightAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(7));
        var right = Support.relation("r", 3, List.of(), new Random(8));
        var lKey = Catalyst.<Attribute>list(left.output()).get(0);
        var rKey = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = Catalyst.<Expression>some(new EqualTo(rKey, lKey));
        var plan = new Join(left, right, LeftAnti$.MODULE$, cond, JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinToSemiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinToSemiJoin.json"));

    @Test
    void semiJoinsLeftAgainstDistinctRightKeys() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var distinctRight = new Aggregate(Catalyst.<Expression>seq(List.of(r)),
                Catalyst.<NamedExpression>seq(List.of(r)), right, scala.Option.empty());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(l)),
                new Join(left, distinctRight, Inner$.MODULE$, Catalyst.<Expression>some(new EqualTo(l, r)),
                        JoinHint$.MODULE$.NONE()));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Join j
                && j.joinType().equals(LeftSemi$.MODULE$) && j.left().equals(left) && j.right().equals(right));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOuterJoinAgainstDistinctRightKeysAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(3));
        var right = Support.relation("r", 1, List.of(0), new Random(4));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var distinctRight = new Aggregate(Catalyst.<Expression>seq(List.of(r)),
                Catalyst.<NamedExpression>seq(List.of(r)), right, scala.Option.empty());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(l)),
                new Join(left, distinctRight, LeftOuter$.MODULE$, Catalyst.<Expression>some(new EqualTo(l, r)),
                        JoinHint$.MODULE$.NONE()));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinAgainstPlainRightAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(5));
        var right = Support.relation("r", 1, List.of(0), new Random(6));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(l)),
                new Join(left, right, Inner$.MODULE$, Catalyst.<Expression>some(new EqualTo(l, r)),
                        JoinHint$.MODULE$.NONE()));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInnerJoinWithoutParentProjectAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(7));
        var right = Support.relation("r", 1, List.of(0), new Random(8));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var distinctRight = new Aggregate(Catalyst.<Expression>seq(List.of(r)),
                Catalyst.<NamedExpression>seq(List.of(r)), right, scala.Option.empty());
        LogicalPlan plan = new Join(left, distinctRight, Inner$.MODULE$, Catalyst.<Expression>some(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

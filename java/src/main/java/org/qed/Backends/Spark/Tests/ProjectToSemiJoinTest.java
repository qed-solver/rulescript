package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectToSemiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectToSemiJoin.json"));

    @Test
    void rewritesInnerJoinOnDistinctKeyIntoSemiJoin() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var agg = new Aggregate(Catalyst.<Expression>seq(List.of(r.get(1))), Catalyst.<NamedExpression>seq(List.of(r.get(1))),
                right, Option.<AggregateHint>empty());
        var join = new Join(left, agg, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l.get(0), r.get(1))),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(l.get(1), l.get(0))), join);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var project = (Project) rewritten;
        var items = Catalyst.<NamedExpression>list(project.projectList());
        assertEquals(l.get(1).exprId(), items.get(0).exprId());
        assertEquals(l.get(0).exprId(), items.get(1).exprId());
        assertTrue(project.child() instanceof Join);
        var semi = (Join) project.child();
        assertEquals(LeftSemi$.MODULE$, semi.joinType());
        assertTrue(semi.right() instanceof LocalRelation);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var agg = new Aggregate(Catalyst.<Expression>seq(List.of(r.get(1))), Catalyst.<NamedExpression>seq(List.of(r.get(1))),
                right, Option.<AggregateHint>empty());
        var join = new Join(left, agg, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l.get(0), r.get(1))),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(l.get(1), l.get(0))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesGroupByWithTwoKeysAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var agg = new Aggregate(Catalyst.<Expression>seq(List.of(r.get(1), r.get(0))),
                Catalyst.<NamedExpression>seq(List.of(r.get(1), r.get(0))), right, Option.<AggregateHint>empty());
        var join = new Join(left, agg, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l.get(0), r.get(1))),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(l.get(1), l.get(0))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectThatKeepsRightColumnAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var agg = new Aggregate(Catalyst.<Expression>seq(List.of(r.get(1))), Catalyst.<NamedExpression>seq(List.of(r.get(1))),
                right, Option.<AggregateHint>empty());
        var join = new Join(left, agg, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l.get(0), r.get(1))),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(r.get(1), l.get(0))), join);
        assertEquals(plan, rule.apply(plan));
    }
}

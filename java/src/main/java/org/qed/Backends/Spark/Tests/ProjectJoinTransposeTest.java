package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
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

class ProjectJoinTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectJoinTranspose.json"));

    private static Project named(org.apache.spark.sql.catalyst.expressions.Expression e, String name, org.apache.spark.sql.catalyst.plans.logical.LogicalPlan child) {
        var alias = Alias$.MODULE$.apply(e, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        return new Project(Catalyst.seq(List.of(alias)), child);
    }

    @Test
    void transposesProjectOverInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new EqualTo(new IsNull(l), new IsNull(r));
        var join = new Join(left, right, Inner$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond),
                JoinHint$.MODULE$.NONE());
        var plan = named(new Or(new IsNull(l), new IsNull(r)), "g", join);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.child() instanceof Join j
                && j.left() instanceof Project && j.right() instanceof Project
                && j.joinType().equals(Inner$.MODULE$) && j.condition().isDefined());
        assertEquals(Catalyst.list(plan.output()).size(), Catalyst.list(rewritten.output()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new EqualTo(new IsNull(l), new IsNull(r));
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond),
                JoinHint$.MODULE$.NONE());
        var plan = named(new Or(new IsNull(l), new IsNull(r)), "g", join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesConditionOnOneSideAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new EqualTo(new IsNull(l), new IsNull(l));
        var join = new Join(left, right, Inner$.MODULE$, Option.<org.apache.spark.sql.catalyst.expressions.Expression>apply(cond),
                JoinHint$.MODULE$.NONE());
        var plan = named(new Or(new IsNull(l), new IsNull(r)), "g", join);
        assertEquals(plan, rule.apply(plan));
    }
}

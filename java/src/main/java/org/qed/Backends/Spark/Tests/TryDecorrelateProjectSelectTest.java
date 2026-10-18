package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryDecorrelateProjectSelectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryDecorrelateProjectSelect.json"));

    @Test
    void movesSelectIntoLeftJoinCondition() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var r1 = Catalyst.<Attribute>list(right.output()).get(1);
        var selected = new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(2)), right);
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(r1)), selected);
        var plan = new Join(left, projected, LeftOuter$.MODULE$,
                Option.<Expression>apply(new EqualTo(l0, r1)), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        assertInstanceOf(Project.class, rewritten);
        var top = (Project) rewritten;
        assertInstanceOf(Join.class, top.child());
        var join = (Join) top.child();
        assertEquals(LeftOuter$.MODULE$, join.joinType());
        assertInstanceOf(Project.class, join.right());
        assertInstanceOf(LocalRelation.class, ((Project) join.right()).child());
        assertTrue(join.condition().isDefined());
        assertEquals(2, Catalyst.conjuncts(join.condition().get()).size());
        assertEquals(Catalyst.list(plan.output()).size(), Catalyst.list(top.projectList()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesInnerJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var r1 = Catalyst.<Attribute>list(right.output()).get(1);
        var selected = new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(2)), right);
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(r1)), selected);
        var plan = new Join(left, projected, Inner$.MODULE$,
                Option.<Expression>apply(new EqualTo(l0, r1)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithoutSelectAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 2, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r1 = Catalyst.<Attribute>list(right.output()).get(1);
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(r1)), right);
        var plan = new Join(left, projected, LeftOuter$.MODULE$,
                Option.<Expression>apply(new EqualTo(l0, r1)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

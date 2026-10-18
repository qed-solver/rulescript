package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneProjectSetColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneProjectSetCols.json"));

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void prunesUnusedColumnOfInnerJoinUnderProject() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("E", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var e0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new GreaterThan(l0, e0)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(named(new Add(l0, e0), "g"))), join);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        assertEquals(1, Catalyst.list(top.projectList()).size());
        assertTrue(top.child() instanceof Join);
        var rewrittenJoin = (Join) top.child();
        assertTrue(rewrittenJoin.left() instanceof Project);
        var prunedLeft = (Project) rewrittenJoin.left();
        assertSame(left, prunedLeft.child());
        assertEquals(1, Catalyst.list(prunedLeft.projectList()).size());
        assertSame(right, rewrittenJoin.right());
        assertTrue(rewrittenJoin.condition().isDefined());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("E", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var e0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new GreaterThan(l0, e0)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(named(new Add(l0, e0), "g"))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionReadingPrunedColumnAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("E", 1, List.of(), new Random(2));
        var attrs = Catalyst.<Attribute>list(left.output());
        var e0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new GreaterThan(attrs.get(0), e0)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(named(new Add(attrs.get(0), attrs.get(1)), "g"))),
                join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesCrossJoinAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("E", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var e0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(named(new Add(l0, e0), "g"))), join);
        assertEquals(plan, rule.apply(plan));
    }
}

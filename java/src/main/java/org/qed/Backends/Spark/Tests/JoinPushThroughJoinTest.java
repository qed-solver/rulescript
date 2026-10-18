package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
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

class JoinPushThroughJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinPushThroughJoin.json"));

    private static Expression sa(Attribute a) {
        return new GreaterThan(a, Literal$.MODULE$.apply(1));
    }

    private static Expression sb(Attribute b) {
        return new LessThan(b, Literal$.MODULE$.apply(3));
    }

    private static Expression st(Attribute a, Attribute b) {
        return new GreaterThan(a, b);
    }

    private static Expression tc(Attribute c) {
        return new LessThan(Literal$.MODULE$.apply(0), c);
    }

    private static Attribute column(LogicalPlan plan, int index) {
        return Catalyst.<Attribute>list(plan.output()).get(index);
    }

    @Test
    void pushesTheOuterJoinBelowTheInnerJoin() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var ra = column(a, 0);
        var rb = column(b, 0);
        var rc = column(c, 0);
        var ab = new Join(a, b, Inner$.MODULE$,
                Catalyst.<Expression>some(new And(sa(ra), sb(rb))), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                Catalyst.<Expression>some(new And(st(ra, rb), tc(rc))), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var project = (Project) rewritten;
        assertEquals(List.of(ra, rb, rc), Catalyst.<NamedExpression>list(project.projectList()));
        assertTrue(project.child() instanceof Join);
        var outer = (Join) project.child();
        assertEquals(b, outer.right());
        assertTrue(outer.left() instanceof Join);
        var inner = (Join) outer.left();
        assertEquals(a, inner.left());
        assertEquals(c, inner.right());
        assertEquals(Catalyst.<Expression>some(new And(sa(ra), tc(rc))), inner.condition());
        assertEquals(Catalyst.<Expression>some(new And(st(ra, rb), sb(rb))), outer.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOuterJoinAboveTheInnerJoinAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var ra = column(a, 0);
        var rb = column(b, 0);
        var rc = column(c, 0);
        var ab = new Join(a, b, Inner$.MODULE$,
                Catalyst.<Expression>some(new And(sa(ra), sb(rb))), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, LeftOuter$.MODULE$,
                Catalyst.<Expression>some(new And(st(ra, rb), tc(rc))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesARightNestedJoinAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var ra = column(a, 0);
        var rb = column(b, 0);
        var rc = column(c, 0);
        var bc = new Join(b, c, Inner$.MODULE$,
                Catalyst.<Expression>some(new And(sb(rb), tc(rc))), JoinHint$.MODULE$.NONE());
        var plan = new Join(a, bc, Inner$.MODULE$,
                Catalyst.<Expression>some(new And(sa(ra), st(ra, rb))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAJoinWithAMissingConjunctAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var ra = column(a, 0);
        var rb = column(b, 0);
        var rc = column(c, 0);
        var ab = new Join(a, b, Inner$.MODULE$,
                Catalyst.<Expression>some(new And(sa(ra), sb(rb))), JoinHint$.MODULE$.NONE());
        var plan = new Join(ab, c, Inner$.MODULE$,
                Catalyst.<Expression>some(st(ra, rb)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransposeWindowTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TransposeWindow.json"));

    @Test
    void transposesStackedWindowsToCoarseOuterFirst() {
        var plan = stackedWindows(Inner$.MODULE$);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(ids(plan), ids(rewritten));
        var top = rewritten instanceof Project project ? project.child() : rewritten;
        assertTrue(top instanceof Join);
        var outer = (Join) top;
        assertTrue(outer.right() instanceof Aggregate fine && fine.groupingExpressions().size() == 2);
        assertTrue(outer.left() instanceof Project inner && inner.child() instanceof Join);
        var lower = (Join) ((Project) outer.left()).child();
        assertTrue(lower.right() instanceof Aggregate coarse && coarse.groupingExpressions().size() == 1);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesWindowsAloneWhenOuterJoinIsNotInner() {
        var plan = stackedWindows(LeftOuter$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    private static List<Object> ids(LogicalPlan plan) {
        var out = new ArrayList<Object>();
        Catalyst.<Attribute>list(plan.output()).forEach(a -> out.add(a.exprId()));
        return out;
    }

    private static LogicalPlan stackedWindows(JoinType outerJoin) {
        var base = Support.relation("r", 3, List.of(0, 1, 2), new Random(7));
        var r = Catalyst.<Attribute>list(base.output());
        var innerInput = (LocalRelation) base.newInstance();
        var inner = window(innerInput, 2, "innerW");
        var innerOut = Catalyst.<Attribute>list(inner.output());
        var joinAB = new Join(base, inner, Inner$.MODULE$,
                Option.<Expression>apply(and(eq(r.get(0), innerOut.get(0)), eq(r.get(1), innerOut.get(1)))),
                JoinHint$.MODULE$.NONE());
        var ab = Catalyst.<Attribute>list(joinAB.output());
        var w2 = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(ab.get(0), ab.get(1), ab.get(2), ab.get(5))), joinAB);
        var outerInput = (LocalRelation) base.newInstance();
        var outer = window(outerInput, 1, "outerW");
        var outerOut = Catalyst.<Attribute>list(outer.output());
        var w2Out = Catalyst.<Attribute>list(w2.output());
        var joinA = new Join(w2, outer, outerJoin,
                Option.<Expression>apply(eq(w2Out.get(0), outerOut.get(0))), JoinHint$.MODULE$.NONE());
        var a = Catalyst.<Attribute>list(joinA.output());
        return new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(a.get(0), a.get(1), a.get(2), a.get(3), a.get(5))), joinA);
    }

    private static Aggregate window(LocalRelation input, int groups, String name) {
        var attrs = Catalyst.<Attribute>list(input.output());
        var keys = new ArrayList<NamedExpression>(attrs.subList(0, groups));
        var max = AggregateExpression$.MODULE$.apply(new Max(attrs.get(2)), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var items = new ArrayList<NamedExpression>(keys);
        items.add(Alias$.MODULE$.apply(max, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of())));
        return new Aggregate(Catalyst.<Expression>seq(new ArrayList<Expression>(attrs.subList(0, groups))), Catalyst.<NamedExpression>seq(items), input,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static Expression eq(Expression left, Expression right) {
        return new EqualTo(left, right);
    }

    private static Expression and(Expression left, Expression right) {
        return new And(left, right);
    }
}

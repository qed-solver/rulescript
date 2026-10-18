package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Coalesce;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryDecorrelateProjectInnerJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryDecorrelateProjectInnerJoin.json"));

    private static Attribute column(LocalRelation relation, int ordinal) {
        return Catalyst.<Attribute>list(relation.output()).get(ordinal);
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static LogicalPlan innerSide(LocalRelation il, LocalRelation ir, JoinType type, boolean projected) {
        var a = column(il, 0);
        var b = column(ir, 0);
        var join = new Join(il, ir, type, Catalyst.<Expression>some(new EqualTo(a, b)), JoinHint$.MODULE$.NONE());
        if (!projected) {
            return join;
        }
        var f = alias(new Coalesce(Catalyst.<Expression>seq(List.of(a, b))), "f");
        return new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(a, f)), join);
    }

    private static LogicalPlan topPlan(LocalRelation l, LogicalPlan right, JoinType type) {
        var l0 = column(l, 0);
        var r = Catalyst.<Attribute>list(right.output());
        var on = new Or(new EqualTo(l0, r.get(0)), new GreaterThan(r.get(1), Literal$.MODULE$.apply(2)));
        return new Join(l, right, type, Catalyst.<Expression>some(on), JoinHint$.MODULE$.NONE());
    }

    @Test
    void movesInnerJoinConditionIntoLeftJoinAndProjectsBack() {
        var l = Support.relation("L", 1, List.of(), new Random(1));
        var il = Support.relation("IL", 1, List.of(), new Random(2));
        var ir = Support.relation("IR", 1, List.of(), new Random(3));
        var plan = topPlan(l, innerSide(il, ir, Inner$.MODULE$, true), LeftOuter$.MODULE$);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.child() instanceof Join outer
                && outer.joinType().equals(LeftOuter$.MODULE$) && outer.condition().isDefined()
                && Catalyst.conjuncts(outer.condition().get()).size() == 2
                && outer.right() instanceof Project right && right.child() instanceof Join inner
                && inner.joinType().equals(Inner$.MODULE$) && inner.condition().isEmpty());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesPlanAloneWhenInnerJoinIsLeftOuter() {
        var l = Support.relation("L", 1, List.of(), new Random(1));
        var il = Support.relation("IL", 1, List.of(), new Random(2));
        var ir = Support.relation("IR", 1, List.of(), new Random(3));
        var plan = topPlan(l, innerSide(il, ir, LeftOuter$.MODULE$, true), LeftOuter$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesPlanAloneWhenTopJoinIsInner() {
        var l = Support.relation("L", 1, List.of(), new Random(1));
        var il = Support.relation("IL", 1, List.of(), new Random(2));
        var ir = Support.relation("IR", 1, List.of(), new Random(3));
        var plan = topPlan(l, innerSide(il, ir, Inner$.MODULE$, true), Inner$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesPlanAloneWhenLeftSideHasTwoColumns() {
        var l = Support.relation("L", 2, List.of(), new Random(1));
        var il = Support.relation("IL", 1, List.of(), new Random(2));
        var ir = Support.relation("IR", 1, List.of(), new Random(3));
        var plan = topPlan(l, innerSide(il, ir, Inner$.MODULE$, true), LeftOuter$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesPlanAloneWhenRightSideIsNotProjected() {
        var l = Support.relation("L", 1, List.of(), new Random(1));
        var il = Support.relation("IL", 1, List.of(), new Random(2));
        var ir = Support.relation("IR", 1, List.of(), new Random(3));
        var plan = topPlan(l, innerSide(il, ir, Inner$.MODULE$, false), LeftOuter$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }
}

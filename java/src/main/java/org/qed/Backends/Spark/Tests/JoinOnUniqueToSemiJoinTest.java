package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
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

class JoinOnUniqueToSemiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinOnUniqueToSemiJoin.json"));

    private static Aggregate uniqueOn(LogicalPlan child, Attribute key) {
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)), Catalyst.<NamedExpression>seq(List.of(key)),
                child, Option.<AggregateHint>empty());
    }

    private static LocalRelation duplicatedRows(String name) {
        var attr = Support.attribute(name + "_0", false);
        var rows = List.<InternalRow>of(new GenericInternalRow(new Object[] {1}),
                new GenericInternalRow(new Object[] {1}));
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(attr)), Catalyst.<InternalRow>seq(rows), false,
                Match.noStream());
    }

    @Test
    void innerJoinOnUniqueKeyProjectingLeftBecomesSemiJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var base = Support.relation("r", 1, List.of(), new Random(2));
        var right = uniqueOn(base, Catalyst.<Attribute>list(base.output()).get(0));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(l)), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Join j && j.joinType() == LeftSemi$.MODULE$);
        var projected = Catalyst.<Attribute>list(((Project) rewritten).output());
        assertEquals(List.of(l.exprId()), projected.stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leftOuterJoinIsNotRewritten() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var base = Support.relation("r", 1, List.of(), new Random(2));
        var right = uniqueOn(base, Catalyst.<Attribute>list(base.output()).get(0));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(l)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void projectingTheRightColumnIsNotRewritten() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var base = Support.relation("r", 1, List.of(), new Random(2));
        var right = uniqueOn(base, Catalyst.<Attribute>list(base.output()).get(0));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(r)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nonUniqueRightSideIsNotRewritten() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = duplicatedRows("d");
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(l)), join);
        assertEquals(plan, rule.apply(plan));
    }
}

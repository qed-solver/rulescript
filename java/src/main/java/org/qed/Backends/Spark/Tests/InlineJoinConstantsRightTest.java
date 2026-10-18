package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThanOrEqual;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineJoinConstantsRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InlineJoinConstantsRight.json"));

    private static Attribute column(LogicalPlan plan, int ordinal) {
        return Catalyst.<Attribute>list(plan.output()).get(ordinal);
    }

    private static LogicalPlan constant(int value) {
        var c = Support.attribute("c", false);
        var row = new GenericInternalRow(new Object[] {value});
        var values = new LocalRelation(Catalyst.<Attribute>seq(List.of(c)),
                Catalyst.<InternalRow>seq(List.of(row)), false, Match.noStream());
        return new Aggregate(Catalyst.<org.apache.spark.sql.catalyst.expressions.Expression>seq(List.of(c)),
                Catalyst.<NamedExpression>seq(List.of(c)), values, Option.<AggregateHint>empty());
    }

    private static LogicalPlan constantRows(int value, boolean unique) {
        if (unique) {
            return constant(value);
        }
        var c = Support.attribute("c", false);
        var rows = List.<InternalRow>of(new GenericInternalRow(new Object[] {value}),
                new GenericInternalRow(new Object[] {value}));
        return new LocalRelation(Catalyst.<Attribute>seq(List.of(c)),
                Catalyst.<InternalRow>seq(rows), false, Match.noStream());
    }

    @Test
    void inlinesConstantIntoEqualityCondition() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var source = Support.relation("s", 1, List.of(), new Random(2));
        var constant = constant(2);
        var l = column(left, 0);
        var s = column(source, 0);
        var c = column(constant, 0);
        var right = new Join(source, constant, Inner$.MODULE$, Option.apply(new EqualTo(s, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(left, right, Inner$.MODULE$, Option.apply(new EqualTo(l, s)), JoinHint$.MODULE$.NONE());
        var expected = new Join(left, right, Inner$.MODULE$, Option.apply(new EqualTo(l, c)), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void inlinesConstantIntoLessThanCondition() {
        var left = Support.relation("l", 1, List.of(), new Random(3));
        var source = Support.relation("s", 1, List.of(), new Random(4));
        var constant = constant(1);
        var l = column(left, 0);
        var s = column(source, 0);
        var c = column(constant, 0);
        var right = new Join(source, constant, Inner$.MODULE$, Option.apply(new EqualTo(s, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(left, right, Inner$.MODULE$, Option.apply(new LessThan(l, s)), JoinHint$.MODULE$.NONE());
        var expected = new Join(left, right, Inner$.MODULE$, Option.apply(new LessThan(l, c)),
                JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(5));
        var source = Support.relation("s", 1, List.of(), new Random(6));
        var constant = constant(2);
        var l = column(left, 0);
        var s = column(source, 0);
        var c = column(constant, 0);
        var right = new Join(source, constant, Inner$.MODULE$, Option.apply(new EqualTo(s, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.apply(new EqualTo(l, s)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonEqualityRightJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var source = Support.relation("s", 1, List.of(), new Random(8));
        var constant = constant(2);
        var l = column(left, 0);
        var s = column(source, 0);
        var c = column(constant, 0);
        var right = new Join(source, constant, Inner$.MODULE$, Option.apply(new GreaterThanOrEqual(s, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(left, right, Inner$.MODULE$, Option.apply(new EqualTo(l, s)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonUniqueConstantAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(9));
        var source = Support.relation("s", 1, List.of(), new Random(10));
        var constant = constantRows(2, false);
        var l = column(left, 0);
        var s = column(source, 0);
        var c = column(constant, 0);
        var right = new Join(source, constant, Inner$.MODULE$, Option.apply(new EqualTo(s, c)),
                JoinHint$.MODULE$.NONE());
        var plan = new Join(left, right, Inner$.MODULE$, Option.apply(new EqualTo(l, s)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

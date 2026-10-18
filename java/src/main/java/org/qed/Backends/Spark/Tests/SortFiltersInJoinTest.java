package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SortFiltersInJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SortFiltersInJoin.json"));

    @Test
    void swapsTheTwoConjunctsOfAnInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        Expression a = new EqualTo(l.get(0), r.get(0));
        Expression b = new GreaterThan(l.get(0), r.get(0));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new And(a, b)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var join = assertInstanceOf(Join.class, rewritten);
        assertEquals(Inner$.MODULE$, join.joinType());
        assertEquals(List.of(b, a), Catalyst.conjuncts(join.condition().get()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        Expression a = new EqualTo(l.get(0), r.get(0));
        Expression b = new GreaterThan(l.get(0), r.get(0));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new And(a, b)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAThreeConjunctJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        Expression a = new EqualTo(l.get(0), r.get(0));
        Expression b = new GreaterThan(l.get(0), r.get(0));
        Expression c = new LessThan(l.get(0), r.get(0));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(Catalyst.conjunction(List.of(a, b, c))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesADisjunctiveJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        Expression a = new EqualTo(l.get(0), r.get(0));
        Expression b = new GreaterThan(l.get(0), r.get(0));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new Or(a, b)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

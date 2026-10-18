package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PushExtraPredicateThroughJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushExtraPredicateThroughJoin.json"));

    @Test
    void pushesEachSideOfTheDisjunctionBelowAnInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new Or(
                new And(new GreaterThan(x, Literal$.MODULE$.apply(1)), new LessThan(y, Literal$.MODULE$.apply(3))),
                new And(new GreaterThan(x, Literal$.MODULE$.apply(1)), new EqualTo(y, Literal$.MODULE$.apply(2))));
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.some(cond), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var join = assertInstanceOf(Join.class, rewritten);
        var leftFilter = assertInstanceOf(Filter.class, join.left());
        var rightFilter = assertInstanceOf(Filter.class, join.right());
        assertEquals(left, leftFilter.child());
        assertEquals(right, rightFilter.child());
        assertEquals(new Or(new GreaterThan(x, Literal$.MODULE$.apply(1)), new GreaterThan(x, Literal$.MODULE$.apply(1))),
                leftFilter.condition());
        assertEquals(new Or(new LessThan(y, Literal$.MODULE$.apply(3)), new EqualTo(y, Literal$.MODULE$.apply(2))),
                rightFilter.condition());
        assertEquals(Catalyst.some(cond), join.condition());
        assertEquals(Inner$.MODULE$, join.joinType());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new Or(
                new And(new GreaterThan(x, Literal$.MODULE$.apply(1)), new LessThan(y, Literal$.MODULE$.apply(3))),
                new And(new GreaterThan(x, Literal$.MODULE$.apply(1)), new EqualTo(y, Literal$.MODULE$.apply(2))));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Catalyst.some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesADisjunctionWithDifferentLeftPredicateAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new Or(
                new And(new GreaterThan(x, Literal$.MODULE$.apply(1)), new LessThan(y, Literal$.MODULE$.apply(3))),
                new And(new GreaterThan(x, Literal$.MODULE$.apply(2)), new EqualTo(y, Literal$.MODULE$.apply(2))));
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAPlainConjunctionAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new GreaterThan(x, Literal$.MODULE$.apply(1)), new LessThan(y, Literal$.MODULE$.apply(3)));
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

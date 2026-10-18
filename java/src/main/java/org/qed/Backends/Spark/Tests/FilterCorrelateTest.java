package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterCorrelateTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterCorrelate.json"));

    @Test
    void pushesOneSidedPredicatesBelowAnInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Match.joinType("inner"), Option.<Expression>empty(),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(1)),
                new And(new LessThan(y, Literal$.MODULE$.apply(3)), new GreaterThan(x, y))), join);
        var expected = new Filter(new GreaterThan(x, y), new Join(
                new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), left),
                new Filter(new LessThan(y, Literal$.MODULE$.apply(3)), right),
                Match.joinType("inner"), Option.<Expression>empty(), JoinHint$.MODULE$.NONE()));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Match.joinType("left"), Option.<Expression>empty(),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(1)),
                new And(new LessThan(y, Literal$.MODULE$.apply(3)), new GreaterThan(x, y))), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnInnerJoinWithAConditionAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Match.joinType("inner"), Option.<Expression>apply(new EqualTo(x, y)),
                JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(1)),
                new And(new LessThan(y, Literal$.MODULE$.apply(3)), new GreaterThan(x, y))), join);
        assertEquals(plan, rule.apply(plan));
    }
}

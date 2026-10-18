package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Except;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaceExceptWithAntiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ReplaceExceptWithAntiJoin.json"));

    @Test
    void exceptBecomesDistinctAntiJoin() {
        var left = Support.relation("a", 2, List.of(), new Random(1));
        var right = Support.relation("b", 2, List.of(), new Random(2));
        var a = Catalyst.<Attribute>list(left.output());
        var b = Catalyst.<Attribute>list(right.output());
        var plan = new Except(left, right, false);
        var cond = new And(new EqualNullSafe(a.get(0), b.get(0)), new EqualNullSafe(a.get(1), b.get(1)));
        var expected = new Aggregate(Catalyst.seq(List.<Expression>of(a.get(0), a.get(1))),
                Catalyst.seq(List.<NamedExpression>of(a.get(0), a.get(1))),
                new Join(left, right, LeftAnti$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE()),
                Option.empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void exceptWithDifferentNullabilityStillBecomesAntiJoin() {
        var left = Support.relation("a", 2, List.of(0, 1), new Random(3));
        var right = Support.relation("b", 2, List.of(0), new Random(4));
        var plan = new Except(left, right, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Aggregate.class, rewritten);
        var join = assertInstanceOf(Join.class, ((Aggregate) rewritten).child());
        assertSame(LeftAnti$.MODULE$, join.joinType());
        assertSame(left, join.left());
        assertSame(right, join.right());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void exceptAllIsLeftAlone() {
        var left = Support.relation("a", 2, List.of(), new Random(1));
        var right = Support.relation("b", 2, List.of(), new Random(2));
        var plan = new Except(left, right, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void exceptOfThreeColumnsIsLeftAlone() {
        var left = Support.relation("a", 3, List.of(), new Random(1));
        var right = Support.relation("b", 3, List.of(), new Random(2));
        var plan = new Except(left, right, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void intersectIsLeftAlone() {
        var left = Support.relation("a", 2, List.of(), new Random(1));
        var right = Support.relation("b", 2, List.of(), new Random(2));
        var plan = new Intersect(left, right, false);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Except;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyExceptTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyExcept.json"));

    @Test
    void rewritesExceptOfKeyedLeftIntoAntiJoin() {
        var base = Support.relation("L", 1, List.of(0), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new Aggregate(Catalyst.seq(List.of(x)), Catalyst.seq(List.of(x)), base, scala.Option.empty());
        var right = Support.relation("R", 1, List.of(0), new Random(2));
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Except(left, right, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.joinType() == LeftAnti$.MODULE$);
        var join = (Join) rewritten;
        assertEquals(left, join.left());
        assertEquals(right, join.right());
        assertEquals(new EqualNullSafe(x, y), join.condition().get());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesExceptOfUnkeyedLeftAlone() {
        var left = Support.relation("L", 1, List.of(0), new Random(3));
        var right = Support.relation("R", 1, List.of(0), new Random(4));
        var plan = new Except(left, right, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesExceptAllOfKeyedLeftAlone() {
        var base = Support.relation("L", 1, List.of(0), new Random(5));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new Aggregate(Catalyst.seq(List.of(x)), Catalyst.seq(List.of(x)), base, scala.Option.empty());
        var right = Support.relation("R", 1, List.of(0), new Random(6));
        var plan = new Except(left, right, true);
        assertEquals(plan, rule.apply(plan));
    }
}

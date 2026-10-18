package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushSelectCondLeftIntoJoinLeftAndRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushSelectCondLeftIntoJoinLeftAndRight.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, String type, Expression cond) {
        return new Join(left, right, Match.joinType(type), scala.Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void pushesLeftKeyFilterBelowLeftOuterJoin() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var on = new EqualTo(l0, r0);
        var plan = new Filter(new GreaterThan(l0, Literal$.MODULE$.apply(1)), join(left, right, "left", on));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var expected = join(new Filter(new GreaterThan(l0, Literal$.MODULE$.apply(1)), left),
                new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(1)), right), "left", on);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesInnerJoinAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(3));
        var right = Support.relation("r", 1, List.of(0), new Random(4));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(new GreaterThan(l0, Literal$.MODULE$.apply(1)),
                join(left, right, "inner", new EqualTo(l0, r0)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterOnRightKeyAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(5));
        var right = Support.relation("r", 1, List.of(0), new Random(6));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(1)),
                join(left, right, "left", new EqualTo(l0, r0)));
        assertEquals(plan, rule.apply(plan));
    }
}

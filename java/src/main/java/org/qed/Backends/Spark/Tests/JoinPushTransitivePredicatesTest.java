package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
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

class JoinPushTransitivePredicatesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinPushTransitivePredicates.json"));

    @Test
    void pushesRightPredicateToLeftKey() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var two = Literal$.MODULE$.apply(2);
        var key = new EqualTo(l0, r0);
        var plan = new Join(left, new Filter(new GreaterThan(r0, two), right), Inner$.MODULE$,
                Option.<Expression>apply(key), JoinHint$.MODULE$.NONE());
        var expected = new Join(new Filter(new GreaterThan(l0, two), left), new Filter(new GreaterThan(r0, two), right),
                Inner$.MODULE$, Option.<Expression>apply(key), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(2)), right), LeftOuter$.MODULE$,
                Option.<Expression>apply(new EqualTo(l0, r0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonEquiJoinAlone() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(2)), right), Inner$.MODULE$,
                Option.<Expression>apply(new LessThan(l0, r0)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

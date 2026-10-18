package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.FullOuter$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyRightJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyRightJoin.json"));

    @Test
    void fullJoinOfNonNullColumnWithItselfBecomesLeftJoin() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var copy = Catalyst.fresh(base);
        var left = Catalyst.<Attribute>list(base.output()).get(0);
        var right = Catalyst.<Attribute>list(copy.output()).get(0);
        var plan = new Join(base, copy, FullOuter$.MODULE$, Option.apply(new EqualTo(left, right)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.joinType() == LeftOuter$.MODULE$);
        var join = (Join) rewritten;
        assertSame(base, join.left());
        assertSame(copy, join.right());
        assertEquals(Option.apply(new EqualTo(left, right)), join.condition());
        assumeAndCompare(plan, rewritten);
    }

    @Test
    void fullJoinOfNullableColumnIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var copy = Catalyst.fresh(base);
        var left = Catalyst.<Attribute>list(base.output()).get(0);
        var right = Catalyst.<Attribute>list(copy.output()).get(0);
        var plan = new Join(base, copy, FullOuter$.MODULE$, Option.apply(new EqualTo(left, right)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leftJoinIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var copy = Catalyst.fresh(base);
        var left = Catalyst.<Attribute>list(base.output()).get(0);
        var right = Catalyst.<Attribute>list(copy.output()).get(0);
        var plan = new Join(base, copy, LeftOuter$.MODULE$, Option.apply(new EqualTo(left, right)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void fullJoinOnInequalityIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var copy = Catalyst.fresh(base);
        var left = Catalyst.<Attribute>list(base.output()).get(0);
        var right = Catalyst.<Attribute>list(copy.output()).get(0);
        var plan = new Join(base, copy, FullOuter$.MODULE$, Option.apply(new GreaterThan(left, right)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    private static void assumeAndCompare(LogicalPlan before, LogicalPlan after) {
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(before), Support.run(after));
    }
}

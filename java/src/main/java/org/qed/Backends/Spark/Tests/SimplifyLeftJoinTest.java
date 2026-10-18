package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyLeftJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyLeftJoin.json"));

    @Test
    void leftSelfJoinOnNonNullColumnBecomesInnerJoin() {
        var base = Support.relation("t", 1, List.of(0), new Random(7));
        var other = (LocalRelation) base.newInstance();
        var left = Catalyst.<Attribute>list(base.output()).get(0);
        var right = Catalyst.<Attribute>list(other.output()).get(0);
        var cond = new EqualTo(left, right);
        var plan = new Join(base, other, LeftOuter$.MODULE$, Option.apply(cond), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.joinType() == Inner$.MODULE$);
        var join = (Join) rewritten;
        assertEquals(base, join.left());
        assertEquals(other, join.right());
        assertEquals(Option.apply(cond), join.condition());
        if (Support.canExecute()) {
            var expected = new Join(base, other, Inner$.MODULE$, Option.apply(cond), JoinHint$.MODULE$.NONE());
            assertEquals(Support.run(plan), Support.run(rewritten));
            assertEquals(Support.run(expected), Support.run(rewritten));
        } else {
            Assumptions.assumeTrue(false, "Spark cannot execute in this sandbox");
        }
    }

    @Test
    void innerSelfJoinIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(7));
        var other = (LocalRelation) base.newInstance();
        var left = Catalyst.<Attribute>list(base.output()).get(0);
        var right = Catalyst.<Attribute>list(other.output()).get(0);
        var plan = new Join(base, other, Inner$.MODULE$, Option.apply(new EqualTo(left, right)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leftJoinOnNullableColumnIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(7));
        var other = (LocalRelation) base.newInstance();
        var left = Catalyst.<Attribute>list(base.output()).get(0);
        var right = Catalyst.<Attribute>list(other.output()).get(0);
        var plan = new Join(base, other, LeftOuter$.MODULE$, Option.apply(new EqualTo(left, right)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leftJoinOnInequalityIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(7));
        var other = (LocalRelation) base.newInstance();
        var left = Catalyst.<Attribute>list(base.output()).get(0);
        var right = Catalyst.<Attribute>list(other.output()).get(0);
        var plan = new Join(base, other, LeftOuter$.MODULE$, Option.apply(new GreaterThan(left, right)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leftJoinOfDifferentTablesIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(7));
        var other = Support.relation("u", 1, List.of(0), new Random(8));
        var left = Catalyst.<Attribute>list(base.output()).get(0);
        var right = Catalyst.<Attribute>list(other.output()).get(0);
        var plan = new Join(base, other, LeftOuter$.MODULE$, Option.apply(new EqualTo(left, right)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

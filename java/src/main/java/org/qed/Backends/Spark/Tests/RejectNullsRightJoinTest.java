package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.plans.FullOuter$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RejectNullsRightJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RejectNullsRightJoin.json"));

    @Test
    void fullJoinUnderNotNullFilterBecomesLeftJoin() {
        for (int seed = 0; seed < 4; seed++) {
            var left = Support.relation("L", 1, List.of(), new Random(seed));
            var right = Support.relation("R", 1, List.of(), new Random(seed + 100));
            var l0 = Catalyst.<Attribute>list(left.output()).get(0);
            var r0 = Catalyst.<Attribute>list(right.output()).get(0);
            var on = new EqualTo(l0, r0);
            var plan = new Filter(new IsNotNull(l0),
                    new Join(left, right, FullOuter$.MODULE$, Option.<Expression>apply(on), JoinHint$.MODULE$.NONE()));
            var expected = new Filter(new IsNotNull(l0),
                    new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(on), JoinHint$.MODULE$.NONE()));
            var rewritten = rule.apply(plan);
            assertNotEquals(plan, rewritten);
            assertEquals(expected, rewritten);
            if (Support.canExecute()) {
                assertEquals(Support.run(plan), Support.run(rewritten));
            } else {
                Assumptions.assumeTrue(false, "no Spark session in this sandbox; only the shape is checked");
            }
        }
    }

    @Test
    void leftJoinUnderNotNullFilterIsLeftAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(new IsNotNull(l0),
                new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l0, r0)), JoinHint$.MODULE$.NONE()));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nullTestOnLeftColumnIsLeftAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(3));
        var right = Support.relation("R", 1, List.of(), new Random(4));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(new IsNull(l0),
                new Join(left, right, FullOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l0, r0)), JoinHint$.MODULE$.NONE()));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void notNullOnRightColumnIsLeftAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(5));
        var right = Support.relation("R", 1, List.of(), new Random(6));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(new IsNotNull(r0),
                new Join(left, right, FullOuter$.MODULE$, Option.<Expression>apply(new EqualTo(l0, r0)), JoinHint$.MODULE$.NONE()));
        assertEquals(plan, rule.apply(plan));
    }
}

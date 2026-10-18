package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
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

class InferFiltersFromConstraintsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InferFiltersFromConstraints.json"));

    @Test
    void infersFiltersFromEqualityAndLeftConjunct() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(lx, rx), new GreaterThan(lx, Literal$.MODULE$.apply(2)));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var expected = new Join(
                new Filter(new And(new GreaterThan(lx, Literal$.MODULE$.apply(2)), new IsNotNull(lx)), left),
                new Filter(new IsNotNull(rx), right),
                Inner$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(expected, rewritten);
        assertEquals(plan.output(), rewritten.output());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesJoinWithoutLeftConjunct() {
        var left = Support.relation("l", 1, List.of(), new Random(3));
        var right = Support.relation("r", 1, List.of(), new Random(4));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(new EqualTo(lx, rx)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(5));
        var right = Support.relation("r", 1, List.of(), new Random(6));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(lx, rx), new GreaterThan(lx, Literal$.MODULE$.apply(2)));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesJoinWithoutEqualityAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("r", 1, List.of(), new Random(8));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new GreaterThan(rx, lx), new GreaterThan(lx, Literal$.MODULE$.apply(2)));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

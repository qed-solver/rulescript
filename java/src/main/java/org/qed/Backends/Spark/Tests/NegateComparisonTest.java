package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NegateComparisonTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("NegateComparison.json"));

    @Test
    void negatedEqualityOverInnerJoinBecomesInequality() {
        var left = Support.relation("l", 1, java.util.List.of(), new Random(1));
        var right = Support.relation("r", 1, java.util.List.of(), new Random(2));
        var join = new Join(left, right, Inner$.MODULE$, Option.empty(), JoinHint$.MODULE$.NONE());
        var output = Catalyst.<Attribute>list(join.output());
        var x = output.get(0);
        var y = output.get(1);
        var plan = new Filter(new Not(new EqualTo(x, y)), join);
        var expected = new Filter(new Not(new EqualTo(x, y)), join);
        LogicalPlan rewritten = rule.apply(plan);
        assertTrue(rule.matches(plan));
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void plainEqualityFilterIsLeftAlone() {
        var left = Support.relation("l", 1, java.util.List.of(), new Random(1));
        var right = Support.relation("r", 1, java.util.List.of(), new Random(2));
        var join = new Join(left, right, Inner$.MODULE$, Option.empty(), JoinHint$.MODULE$.NONE());
        var output = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new EqualTo(output.get(0), output.get(1)), join);
        assertFalse(rule.matches(plan));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void negatedEqualityOverLeftOuterJoinIsLeftAlone() {
        var left = Support.relation("l", 1, java.util.List.of(), new Random(1));
        var right = Support.relation("r", 1, java.util.List.of(), new Random(2));
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.empty(), JoinHint$.MODULE$.NONE());
        var output = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new Not(new EqualTo(output.get(0), output.get(1))), join);
        assertFalse(rule.matches(plan));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void negatedInequalityIsLeftAlone() {
        var left = Support.relation("l", 1, java.util.List.of(), new Random(1));
        var right = Support.relation("r", 1, java.util.List.of(), new Random(2));
        var join = new Join(left, right, Inner$.MODULE$, Option.empty(), JoinHint$.MODULE$.NONE());
        var output = Catalyst.<Attribute>list(join.output());
        Expression condition = new Not(new LessThan(output.get(0), output.get(1)));
        var plan = new Filter(condition, join);
        assertFalse(rule.matches(plan));
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyZeroCardinalitySemiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyZeroCardinalitySemiJoin.json"));

    private static LocalRelation emptyRelation(String name) {
        var attr = Support.attribute(name, false);
        return new LocalRelation(Catalyst.seq(List.of(attr)), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    @Test
    void semiJoinAgainstEmptyRightBecomesEmptyLeft() {
        var left = Support.relation("Left", 1, List.of(), new Random(1));
        var right = emptyRelation("Right_0");
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        Expression on = new GreaterThan(l, r);
        var plan = new Join(left, right, LeftSemi$.MODULE$, Catalyst.some(on), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation lr && lr.data().isEmpty());
        assertEquals(Catalyst.list(left.output()), Catalyst.list(((LogicalPlan) rewritten).output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(List.of(), Support.run(plan));
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void antiJoinAgainstEmptyRightIsLeftAlone() {
        var left = Support.relation("Left", 1, List.of(), new Random(1));
        var right = emptyRelation("Right_0");
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        Expression on = new GreaterThan(l, r);
        var plan = new Join(left, right, LeftAnti$.MODULE$, Catalyst.some(on), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void semiJoinAgainstNonEmptyRightIsLeftAlone() {
        var left = Support.relation("Left", 1, List.of(), new Random(1));
        var right = Support.relation("Right", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        Expression on = new GreaterThan(l, r);
        var plan = new Join(left, right, LeftSemi$.MODULE$, Catalyst.some(on), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RemoveJoinNotNullConditionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RemoveJoinNotNullCondition.json"));

    @Test
    void dropsNotNullOnNonNullLeftKeyFromLeftJoin() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new org.apache.spark.sql.catalyst.expressions.And(new IsNotNull(l), new EqualTo(l, r));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(condition), JoinHint$.MODULE$.NONE());
        var expected = new Join(left, right, LeftOuter$.MODULE$,
                Catalyst.<Expression>some(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsNotNullWhenLeftKeyIsNullable() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new org.apache.spark.sql.catalyst.expressions.And(new IsNotNull(l), new EqualTo(l, r));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsNotNullWhenJoinIsInner() {
        var left = Support.relation("l", 1, List.of(0), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new org.apache.spark.sql.catalyst.expressions.And(new IsNotNull(l), new EqualTo(l, r));
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.<Expression>some(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

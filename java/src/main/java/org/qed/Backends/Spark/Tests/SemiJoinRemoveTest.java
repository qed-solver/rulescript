package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemiJoinRemoveTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SemiJoinRemove.json"));

    @Test
    void removesSemiJoinBelowMatchingInnerJoin() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        Expression cond = new EqualTo(xa, ya);
        var semi = new Join(x, y, LeftSemi$.MODULE$, Catalyst.some(cond), JoinHint$.MODULE$.NONE());
        var plan = new Join(semi, y, Inner$.MODULE$, Catalyst.some(cond), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.joinType().equals(Inner$.MODULE$)
                && j.left().equals(x) && j.right().equals(y));
        assertEquals(Catalyst.some(cond), ((Join) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsPlanWhenInnerJoinUsesDifferentCondition() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        Expression equal = new EqualTo(xa, ya);
        Expression greater = new GreaterThan(xa, ya);
        var semi = new Join(x, y, LeftSemi$.MODULE$, Catalyst.some(equal), JoinHint$.MODULE$.NONE());
        var plan = new Join(semi, y, Inner$.MODULE$, Catalyst.some(greater), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsPlanWhenLeftInputIsAntiJoin() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        Expression cond = new EqualTo(xa, ya);
        var anti = new Join(x, y, LeftAnti$.MODULE$, Catalyst.some(cond), JoinHint$.MODULE$.NONE());
        var plan = new Join(anti, y, Inner$.MODULE$, Catalyst.some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

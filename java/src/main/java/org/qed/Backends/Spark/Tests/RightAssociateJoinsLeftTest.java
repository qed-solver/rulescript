package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
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

class RightAssociateJoinsLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RightAssociateJoinsLeft.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, String type, Expression cond) {
        return new Join(left, right, Match.joinType(type), scala.Option.apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void reassociatesInnerJoinConditionsToTheLeft() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var aa = Catalyst.<Attribute>list(a.output()).get(0);
        var ba = Catalyst.<Attribute>list(b.output()).get(0);
        var ca = Catalyst.<Attribute>list(c.output()).get(0);
        var plan = join(a, join(b, c, "inner", null), "inner",
                new And(new EqualTo(aa, ba), new EqualTo(ba, ca)));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join);
        var outer = (Join) rewritten;
        assertTrue(outer.left() instanceof Join);
        assertEquals(c, outer.right());
        var inner = (Join) outer.left();
        assertEquals(a, inner.left());
        assertEquals(b, inner.right());
        assertTrue(inner.condition().isDefined());
        assertEquals(new EqualTo(aa, ba), inner.condition().get());
        assertTrue(outer.condition().isDefined());
        assertEquals(new EqualTo(ba, ca), outer.condition().get());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesInnerJoinWithConditionOnInnerPairAlone() {
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var aa = Catalyst.<Attribute>list(a.output()).get(0);
        var ba = Catalyst.<Attribute>list(b.output()).get(0);
        var ca = Catalyst.<Attribute>list(c.output()).get(0);
        var plan = join(a, join(b, c, "inner", new EqualTo(ba, ca)), "inner", new EqualTo(aa, ba));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftOuterOuterJoinAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var aa = Catalyst.<Attribute>list(a.output()).get(0);
        var ba = Catalyst.<Attribute>list(b.output()).get(0);
        var ca = Catalyst.<Attribute>list(c.output()).get(0);
        var plan = join(a, join(b, c, "inner", null), "left",
                new And(new EqualTo(aa, ba), new EqualTo(ba, ca)));
        assertEquals(plan, rule.apply(plan));
    }
}

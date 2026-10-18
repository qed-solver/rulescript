package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushLeftSemiLeftAntiThroughJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushLeftSemiLeftAntiThroughJoin.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, JoinType type, Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void pushesSemiJoinIntoLeftLegOfInnerJoin() {
        var x = Support.relation("X", 1, List.of(), new Random(1));
        var y = Support.relation("Y", 1, List.of(), new Random(2));
        var z = Support.relation("Z", 1, List.of(), new Random(3));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var za = Catalyst.<Attribute>list(z.output()).get(0);
        var inner = join(x, y, Inner$.MODULE$, new EqualTo(xa, ya));
        var plan = join(inner, z, LeftSemi$.MODULE$, new GreaterThan(xa, za));
        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join, "rewritten root is a join");
        var top = (Join) rewritten;
        assertEquals(Inner$.MODULE$, top.joinType());
        assertEquals(new EqualTo(xa, ya), top.condition().get());
        assertTrue(top.right().equals(y), "Y stays on the right of the top inner join");
        assertTrue(top.left() instanceof Join, "left of the top join is the pushed semi join");
        var semi = (Join) top.left();
        assertEquals(LeftSemi$.MODULE$, semi.joinType());
        assertEquals(new GreaterThan(xa, za), semi.condition().get());
        assertEquals(x, semi.left());
        assertEquals(z, semi.right());
        assertEquals(
                Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesSemiJoinOverLeftOuterJoinAlone() {
        var x = Support.relation("X", 1, List.of(), new Random(1));
        var y = Support.relation("Y", 1, List.of(), new Random(2));
        var z = Support.relation("Z", 1, List.of(), new Random(3));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var za = Catalyst.<Attribute>list(z.output()).get(0);
        var outer = join(x, y, LeftOuter$.MODULE$, new EqualTo(xa, ya));
        var plan = join(outer, z, LeftSemi$.MODULE$, new GreaterThan(xa, za));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesLeftAntiJoinAlone() {
        var x = Support.relation("X", 1, List.of(), new Random(1));
        var y = Support.relation("Y", 1, List.of(), new Random(2));
        var z = Support.relation("Z", 1, List.of(), new Random(3));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var za = Catalyst.<Attribute>list(z.output()).get(0);
        var inner = join(x, y, Inner$.MODULE$, new EqualTo(xa, ya));
        var plan = join(inner, z, LeftAnti$.MODULE$, new GreaterThan(xa, za));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSemiJoinConditionReferencingRightLegAlone() {
        var x = Support.relation("X", 1, List.of(), new Random(1));
        var y = Support.relation("Y", 1, List.of(), new Random(2));
        var z = Support.relation("Z", 1, List.of(), new Random(3));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var za = Catalyst.<Attribute>list(z.output()).get(0);
        var inner = join(x, y, Inner$.MODULE$, new EqualTo(xa, ya));
        var plan = join(inner, z, LeftSemi$.MODULE$, new GreaterThan(ya, za));
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemiJoinFilterTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SemiJoinFilterTranspose.json"));

    @Test
    void pushesFilterOnLeftBelowSemiJoin() {
        var x = Support.relation("x", 2, List.of(), new Random(1));
        var y = Support.relation("y", 2, List.of(), new Random(2));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var filtered = new Filter(new GreaterThan(xa, Literal$.MODULE$.apply(1)), x);
        var plan = new Join(filtered, y, LeftSemi$.MODULE$, Option.apply(new EqualTo(xa, ya)),
                JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() instanceof Join j
                && j.left() == x && j.right() == y && j.joinType() == LeftSemi$.MODULE$);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesSemiJoinWithoutFilterAlone() {
        var x = Support.relation("x", 2, List.of(), new Random(1));
        var y = Support.relation("y", 2, List.of(), new Random(2));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var plan = new Join(x, y, LeftSemi$.MODULE$, Option.apply(new EqualTo(xa, ya)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAntiJoinWithFilterAlone() {
        var x = Support.relation("x", 2, List.of(), new Random(1));
        var y = Support.relation("y", 2, List.of(), new Random(2));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var filtered = new Filter(new GreaterThan(xa, Literal$.MODULE$.apply(1)), x);
        var plan = new Join(filtered, y, LeftAnti$.MODULE$, Option.apply(new EqualTo(xa, ya)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterAboveSemiJoinAlone() {
        var x = Support.relation("x", 2, List.of(), new Random(1));
        var y = Support.relation("y", 2, List.of(), new Random(2));
        var xa = Catalyst.<Attribute>list(x.output()).get(0);
        var ya = Catalyst.<Attribute>list(y.output()).get(0);
        var semi = new Join(x, y, LeftSemi$.MODULE$, Option.apply(new EqualTo(xa, ya)), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(xa, Literal$.MODULE$.apply(1)), semi);
        assertEquals(plan, rule.apply(plan));
    }
}

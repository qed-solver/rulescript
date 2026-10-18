package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushSelectIntoJoinLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushSelectIntoJoinLeft.json"));

    @Test
    void pushesLeftBoundConjunctIntoLeftInput() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var on = new EqualTo(lx, rx);
        var join = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(on), JoinHint$.MODULE$.NONE());
        var leftBound = new GreaterThan(lx, Literal$.MODULE$.apply(2));
        var unbound = new LessThan(lx, rx);
        var plan = new Filter(new And(leftBound, unbound), join);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter top && top.condition().equals(unbound)
                && top.child() instanceof Join j && j.joinType() == LeftOuter$.MODULE$
                && j.condition().equals(Option.<Expression>apply(on))
                && j.left() instanceof Filter pushed && pushed.condition().equals(leftBound)
                && pushed.child() == left
                && j.right() == right);
        if (Support.canExecute()) {
            assertEquals(Support.run(plan), Support.run(rewritten));
        }
    }

    @Test
    void leavesInnerJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var lx = Catalyst.<Attribute>list(left.output()).get(0);
        var rx = Catalyst.<Attribute>list(right.output()).get(0);
        var on = new EqualTo(lx, rx);
        var join = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(on), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new And(new GreaterThan(lx, Literal$.MODULE$.apply(2)), new LessThan(lx, rx)), join);
        assertEquals(plan, rule.apply(plan));
    }
}

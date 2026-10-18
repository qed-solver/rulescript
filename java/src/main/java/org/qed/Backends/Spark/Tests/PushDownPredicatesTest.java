package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushDownPredicatesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushDownPredicates.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, org.apache.spark.sql.catalyst.plans.JoinType type,
                             Expression cond) {
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void pushesLeftOnlyFilterBelowInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var joinCond = new EqualTo(l0, r0);
        var plan = new Filter(new GreaterThan(l0, Literal$.MODULE$.apply(2)),
                join(left, right, Inner$.MODULE$, joinCond));
        var expected = join(new Filter(new GreaterThan(l0, Literal$.MODULE$.apply(2)), left), right, Inner$.MODULE$,
                joinCond);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join j && j.left() instanceof Filter && j.right() == right);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(3));
        var right = Support.relation("r", 1, List.of(), new Random(4));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(new GreaterThan(l0, Literal$.MODULE$.apply(2)),
                join(left, right, LeftOuter$.MODULE$, new EqualTo(l0, r0)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesRightOnlyFilterAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(5));
        var right = Support.relation("r", 1, List.of(), new Random(6));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(2)),
                join(left, right, Inner$.MODULE$, new EqualTo(l0, r0)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesMixedConjunctionAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(7));
        var right = Support.relation("r", 1, List.of(), new Random(8));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var mixed = new And(new GreaterThan(l0, Literal$.MODULE$.apply(2)), new GreaterThan(r0, Literal$.MODULE$.apply(1)));
        var plan = new Filter(mixed, join(left, right, Inner$.MODULE$, new EqualTo(l0, r0)));
        assertEquals(plan, rule.apply(plan));
    }
}

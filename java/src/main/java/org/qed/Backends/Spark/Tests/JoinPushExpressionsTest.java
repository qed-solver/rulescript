package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinPushExpressionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinPushExpressions.json"));

    private static Expression leftKey(Attribute l) {
        return Add$.MODULE$.apply(l, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
    }

    private static Expression rightKey(Attribute r) {
        return Add$.MODULE$.apply(r, Literal$.MODULE$.apply(2), Add$.MODULE$.apply$default$3());
    }

    private static Join innerJoin(LogicalPlan left, LogicalPlan right, Expression cond) {
        return new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void pushesKeyExpressionsBelowAnInnerJoin() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = innerJoin(left, right, new And(new EqualTo(leftKey(l), rightKey(r)), new LessThan(l, r)));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.child() instanceof Join join
                && join.joinType() == Inner$.MODULE$ && join.left() instanceof Project
                && join.right() instanceof Project && join.condition().get() instanceof And);
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var cond = new And(new EqualTo(leftKey(l), rightKey(r)), new LessThan(l, r));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAJoinWithoutResidualPredicateAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = innerJoin(left, right, new EqualTo(leftKey(l), rightKey(r)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAJoinWithTwoColumnLeftInputAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = innerJoin(left, right, new And(new EqualTo(leftKey(l), rightKey(r)), new LessThan(l, r)));
        assertEquals(plan, rule.apply(plan));
    }
}

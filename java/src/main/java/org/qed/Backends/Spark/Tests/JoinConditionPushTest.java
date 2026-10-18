package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JoinConditionPushTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinConditionPush.json"));

    private static Join join(LogicalPlan left, LogicalPlan right, JoinType type, Expression cond) {
        return new Join(left, right, type, scala.Option.apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void pushesOneSideConjunctsIntoTheInputs() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var leftCond = new GreaterThan(l, Literal$.MODULE$.apply(2));
        var rightCond = new LessThan(r, Literal$.MODULE$.apply(3));
        var crossCond = new EqualTo(l, r);
        var plan = join(left, right, Inner$.MODULE$, new And(new And(leftCond, rightCond), crossCond));
        var expected = join(new Filter(leftCond, left), new Filter(rightCond, right), Inner$.MODULE$, crossCond);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(left, right, LeftOuter$.MODULE$,
                new And(new And(new GreaterThan(l, Literal$.MODULE$.apply(2)), new LessThan(r, Literal$.MODULE$.apply(3))),
                        new EqualTo(l, r)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoLeftOnlyConjunctsAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = join(left, right, Inner$.MODULE$,
                new And(new And(new GreaterThan(l, Literal$.MODULE$.apply(2)), new GreaterThan(l, Literal$.MODULE$.apply(5))),
                        new EqualTo(l, r)));
        assertEquals(plan, rule.apply(plan));
    }
}

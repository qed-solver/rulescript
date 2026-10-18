package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
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

class HoistSelectExistsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("HoistSelectExists.json"));

    @Test
    void hoistsUncorrelatedExistsIntoSemiJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("s", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var s = Catalyst.<Attribute>list(right.output()).get(0);
        var sub = new Filter(new GreaterThan(s, Literal$.MODULE$.apply(1)), right);
        var exists = new Exists(sub, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), Option.empty());
        var rest = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new And(exists, rest), left);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.condition().equals(rest));
        var join = (Join) ((Filter) rewritten).child();
        assertEquals(LeftSemi$.MODULE$, join.joinType());
        assertEquals(left, join.left());
        assertEquals(new Filter(new GreaterThan(s, Literal$.MODULE$.apply(1)), right), join.right());
        assertEquals(plan.output(), rewritten.output());
        assertEquals(new Filter(rest, new Join(left, new Filter(new GreaterThan(s, Literal$.MODULE$.apply(1)), right),
                LeftSemi$.MODULE$, Option.empty(), JoinHint$.MODULE$.NONE())), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNegatedExistsAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("s", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var s = Catalyst.<Attribute>list(right.output()).get(0);
        var sub = new Filter(new GreaterThan(s, Literal$.MODULE$.apply(1)), right);
        var exists = new Exists(sub, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), Option.empty());
        var plan = new Filter(new And(new Not(exists), new GreaterThan(x, Literal$.MODULE$.apply(2))), left);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionWithExistsAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("s", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var s = Catalyst.<Attribute>list(right.output()).get(0);
        var sub = new Filter(new GreaterThan(s, Literal$.MODULE$.apply(1)), right);
        var exists = new Exists(sub, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), Option.empty());
        var plan = new Filter(new Or(exists, new GreaterThan(x, Literal$.MODULE$.apply(2))), left);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
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

class JoinReduceExpressionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinReduceExpressions.json"));

    @Test
    void replacesConjunctWithFalseOnInnerJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new And(new GreaterThan(l, r), Literal$.MODULE$.FalseLiteral());
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(condition), JoinHint$.MODULE$.NONE());
        var expected = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(Literal$.MODULE$.FalseLiteral()),
                JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOuterJoinAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new And(new GreaterThan(l, r), Literal$.MODULE$.FalseLiteral());
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionWithFalseAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new Or(new GreaterThan(l, r), Literal$.MODULE$.FalseLiteral());
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesConjunctWithTrueAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var condition = new And(new GreaterThan(l, r), Literal$.MODULE$.TrueLiteral());
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

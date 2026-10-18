package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
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

class ExpandDisjunctionForJoinInputsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ExpandDisjunctionForJoinInputs.json"));

    @Test
    void expandsDisjunctionOfConjunctionsOnInnerJoin() {
        var left = Support.relation("L", 2, List.of(), new Random(7));
        var right = Support.relation("R", 2, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var pl = new GreaterThan(l.get(0), Literal$.MODULE$.apply(1));
        var rl = new LessThan(l.get(1), Literal$.MODULE$.apply(3));
        var pr = new GreaterThan(r.get(0), Literal$.MODULE$.apply(0));
        var sr = new LessThan(r.get(1), Literal$.MODULE$.apply(2));
        var cond = new Or(new And(pl, pr), new And(rl, sr));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join join && join.left() == left && join.right() == right);
        var expected = new And(new And(cond, new Or(pl, rl)), new Or(pr, sr));
        assertEquals(expected, ((Join) rewritten).condition().get());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(7));
        var right = Support.relation("R", 2, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var pl = new GreaterThan(l.get(0), Literal$.MODULE$.apply(1));
        var rl = new LessThan(l.get(1), Literal$.MODULE$.apply(3));
        var pr = new GreaterThan(r.get(0), Literal$.MODULE$.apply(0));
        var sr = new LessThan(r.get(1), Literal$.MODULE$.apply(2));
        var cond = new Or(new And(pl, pr), new And(rl, sr));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesBranchWithTwoLeftPredicatesAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(7));
        var right = Support.relation("R", 2, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var pl = new GreaterThan(l.get(0), Literal$.MODULE$.apply(1));
        var rl = new LessThan(l.get(1), Literal$.MODULE$.apply(3));
        var pr = new GreaterThan(r.get(0), Literal$.MODULE$.apply(0));
        var leftOnly = new GreaterThan(l.get(0), Literal$.MODULE$.apply(0));
        var cond = new Or(new And(pl, pr), new And(rl, leftOnly));
        var plan = new Join(left, right, Inner$.MODULE$, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

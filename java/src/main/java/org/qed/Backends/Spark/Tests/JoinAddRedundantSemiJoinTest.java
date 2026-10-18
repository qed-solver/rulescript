package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinAddRedundantSemiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("JoinAddRedundantSemiJoin.json"));

    @Test
    void addsSemiJoinBelowInnerJoin() {
        var x = Support.relation("x", 1, List.of(), new Random(1));
        var y = Support.relation("y", 1, List.of(), new Random(2));
        var xc = Catalyst.<Attribute>list(x.output()).get(0);
        var yc = Catalyst.<Attribute>list(y.output()).get(0);
        var cond = new EqualTo(xc, yc);
        var plan = new Join(x, y, Inner$.MODULE$, Option.apply(cond), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var body = rewritten instanceof Project project ? project.child() : rewritten;
        assertTrue(body instanceof Join outer && outer.joinType() == Inner$.MODULE$
                && outer.left() instanceof Join semi && semi.joinType() == LeftSemi$.MODULE$
                && semi.left().equals(x) && semi.right().equals(y));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void addsSemiJoinBelowInnerJoinWithConjunctiveCondition() {
        var x = Support.relation("x", 1, List.of(), new Random(3));
        var y = Support.relation("y", 1, List.of(), new Random(4));
        var xc = Catalyst.<Attribute>list(x.output()).get(0);
        var yc = Catalyst.<Attribute>list(y.output()).get(0);
        var cond = new And(new EqualTo(xc, yc), new GreaterThan(yc, Literal$.MODULE$.apply(1)));
        var plan = new Join(x, y, Inner$.MODULE$, Option.apply(cond), JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var body = rewritten instanceof Project project ? project.child() : rewritten;
        assertTrue(body instanceof Join outer && outer.joinType() == Inner$.MODULE$
                && outer.left() instanceof Join semi && semi.joinType() == LeftSemi$.MODULE$);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftSemiJoinAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(5));
        var y = Support.relation("y", 1, List.of(), new Random(6));
        var xc = Catalyst.<Attribute>list(x.output()).get(0);
        var yc = Catalyst.<Attribute>list(y.output()).get(0);
        var plan = new Join(x, y, LeftSemi$.MODULE$, Option.apply(new EqualTo(xc, yc)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesCrossJoinAlone() {
        var x = Support.relation("x", 1, List.of(), new Random(7));
        var y = Support.relation("y", 1, List.of(), new Random(8));
        var plan = new Join(x, y, Inner$.MODULE$, Option.empty(), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesWiderLeftInputAlone() {
        var x = Support.relation("x", 2, List.of(), new Random(9));
        var y = Support.relation("y", 1, List.of(), new Random(10));
        var xc = Catalyst.<Attribute>list(x.output()).get(0);
        var yc = Catalyst.<Attribute>list(y.output()).get(0);
        var plan = new Join(x, y, Inner$.MODULE$, Option.apply(new EqualTo(xc, yc)), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

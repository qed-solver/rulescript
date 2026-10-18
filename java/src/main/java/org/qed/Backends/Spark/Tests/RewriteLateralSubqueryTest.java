package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LateralSubquery;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.OuterReference;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LateralJoin;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewriteLateralSubqueryTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RewriteLateralSubquery.json"));

    private static Expression outerCondition(Attribute l0, Attribute l1, Attribute s0, Attribute s1) {
        return new Or(new GreaterThan(new OuterReference(l0), s0), new EqualTo(new OuterReference(l1), s1));
    }

    private static Expression plainCondition(Attribute l0, Attribute l1, Attribute s0, Attribute s1) {
        return new Or(new GreaterThan(l0, s0), new EqualTo(l1, s1));
    }

    @Test
    void rewritesLeftLateralJoinIntoLeftJoin() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("s", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var s = Catalyst.<Attribute>list(right.output());
        var subquery = new LateralSubquery(new Filter(outerCondition(l.get(0), l.get(1), s.get(0), s.get(1)), right),
                Catalyst.<Expression>seq(List.of(l.get(0), l.get(1))), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), scala.Option.empty());
        var plan = new LateralJoin(left, subquery, LeftOuter$.MODULE$, scala.Option.empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join join && join.joinType().equals(LeftOuter$.MODULE$)
                && join.left().equals(left) && join.right().equals(right));
        assertEquals(scala.Option.apply(plainCondition(l.get(0), l.get(1), s.get(0), s.get(1))),
                ((Join) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAPlainLeftJoinAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("s", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var s = Catalyst.<Attribute>list(right.output());
        var plan = new Join(left, right, LeftOuter$.MODULE$,
                scala.Option.apply(plainCondition(l.get(0), l.get(1), s.get(0), s.get(1))), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnInnerLateralJoinAlone() {
        var left = Support.relation("l", 2, List.of(), new Random(1));
        var right = Support.relation("s", 2, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output());
        var s = Catalyst.<Attribute>list(right.output());
        var subquery = new LateralSubquery(new Filter(outerCondition(l.get(0), l.get(1), s.get(0), s.get(1)), right),
                Catalyst.<Expression>seq(List.of(l.get(0), l.get(1))), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), scala.Option.empty());
        var plan = new LateralJoin(left, subquery, Inner$.MODULE$, scala.Option.empty());
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushDownLeftSemiAntiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushDownLeftSemiAntiJoin.json"));

    @Test
    void pushesProjectedSemiJoinConditionBelowTheProjection() {
        var x = Support.relation("x", 2, List.of(), new Random(3));
        var y = Support.relation("y", 2, List.of(), new Random(4));
        var xs = Catalyst.<Attribute>list(x.output());
        var ys = Catalyst.<Attribute>list(y.output());
        var sum = add(xs.get(0), xs.get(1));
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum, "p0"), xs.get(0))), x);
        var projectedOut = Catalyst.<Attribute>list(projected.output());
        var cond = predicate(projectedOut.get(0), projectedOut.get(1), ys.get(0), ys.get(1));
        var plan = new Join(projected, y, Match.joinType("semi"), Catalyst.some(cond), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.child() instanceof Join join
                && join.joinType().equals(Match.joinType("semi"))
                && join.left() == x && join.right() == y);
        var outer = (Project) rewritten;
        assertEquals(List.of("p0", xs.get(0).name()),
                Catalyst.list(outer.output()).stream().map(a -> a.name()).toList());
        var join = (Join) outer.child();
        assertEquals(Catalyst.some(predicate(sum, xs.get(0), ys.get(0), ys.get(1))), join.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAntiJoinOverProjectionAlone() {
        var x = Support.relation("x", 2, List.of(), new Random(3));
        var y = Support.relation("y", 2, List.of(), new Random(4));
        var xs = Catalyst.<Attribute>list(x.output());
        var ys = Catalyst.<Attribute>list(y.output());
        var sum = add(xs.get(0), xs.get(1));
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(alias(sum, "p0"), xs.get(0))), x);
        var projectedOut = Catalyst.<Attribute>list(projected.output());
        var cond = predicate(projectedOut.get(0), projectedOut.get(1), ys.get(0), ys.get(1));
        var plan = new Join(projected, y, Match.joinType("anti"), Catalyst.some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSemiJoinOverFilterAlone() {
        var x = Support.relation("x", 2, List.of(), new Random(3));
        var y = Support.relation("y", 2, List.of(), new Random(4));
        var xs = Catalyst.<Attribute>list(x.output());
        var ys = Catalyst.<Attribute>list(y.output());
        var filtered = new Filter(new GreaterThan(xs.get(0), Literal$.MODULE$.apply(1)), x);
        var cond = predicate(xs.get(0), xs.get(1), ys.get(0), ys.get(1));
        var plan = new Join(filtered, y, Match.joinType("semi"), Catalyst.some(cond), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    private static Expression add(Expression a, Expression b) {
        return Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
    }

    private static Expression predicate(Expression a, Expression b, Expression c, Expression d) {
        return new GreaterThan(add(a, b), add(c, d));
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }
}

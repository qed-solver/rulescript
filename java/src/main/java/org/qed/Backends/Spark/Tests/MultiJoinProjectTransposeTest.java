package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MultiJoinProjectTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("MultiJoinProjectTranspose.json"));

    private static Expression sum(Attribute a, Attribute b) {
        return Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
    }

    private static Expression predicate(Expression a, Expression b, Expression c) {
        return new GreaterThan(Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3()), c);
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void pushesTheLeftProjectIntoTheInnerJoin() {
        var left = Support.relation("L", 2, List.of(), new Random(3));
        var right = Support.relation("R", 2, List.of(), new Random(4));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var projected = new Project(Catalyst.seq(List.of(alias(sum(l.get(0), l.get(1)), "tl"))), left);
        var tl = Catalyst.<Attribute>list(projected.output()).get(0);
        var plan = new Join(projected, right, Inner$.MODULE$,
                Catalyst.some(predicate(tl, r.get(0), r.get(1))), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var top = assertInstanceOf(Project.class, rewritten);
        var items = Catalyst.<NamedExpression>list(top.projectList());
        assertEquals(3, items.size());
        assertEquals(sum(l.get(0), l.get(1)), assertInstanceOf(Alias.class, items.get(0)).child());
        assertEquals(r.get(0), items.get(1));
        assertEquals(r.get(1), items.get(2));
        var join = assertInstanceOf(Join.class, top.child());
        assertEquals(left, join.left());
        assertEquals(right, join.right());
        assertEquals(Catalyst.some(predicate(sum(l.get(0), l.get(1)), r.get(0), r.get(1))), join.condition());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinOverAProjectAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(5));
        var right = Support.relation("R", 2, List.of(), new Random(6));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var projected = new Project(Catalyst.seq(List.of(alias(sum(l.get(0), l.get(1)), "tl"))), left);
        var tl = Catalyst.<Attribute>list(projected.output()).get(0);
        var plan = new Join(projected, right, LeftOuter$.MODULE$,
                Catalyst.some(predicate(tl, r.get(0), r.get(1))), JoinHint$.MODULE$.NONE());

        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnInnerJoinWithoutAProjectOnTheLeftAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(7));
        var right = Support.relation("R", 2, List.of(), new Random(8));
        var l = Catalyst.<Attribute>list(left.output());
        var r = Catalyst.<Attribute>list(right.output());
        var plan = new Join(left, right, Inner$.MODULE$,
                Catalyst.some(predicate(l.get(0), r.get(0), r.get(1))), JoinHint$.MODULE$.NONE());

        assertEquals(plan, rule.apply(plan));
    }
}

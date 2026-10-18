package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Multiply$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
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

class SemiJoinProjectTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SemiJoinProjectTranspose.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression sum(Attribute a, Attribute b) {
        return Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
    }

    private static Expression product(Attribute a, Attribute b) {
        return Multiply$.MODULE$.apply(a, b, Multiply$.MODULE$.apply$default$3());
    }

    private static Attribute column(LogicalPlan plan, int i) {
        return Catalyst.<Attribute>list(plan.output()).get(i);
    }

    @Test
    void transposesSemiJoinBelowProjection() {
        var x = Support.relation("x", 2, List.of(), new Random(1));
        var y = Support.relation("y", 2, List.of(), new Random(2));
        var projected = new Project(Catalyst.seq(List.of(
                alias(sum(column(x, 0), column(x, 1)), "p0"),
                alias(product(column(x, 0), column(x, 1)), "p1"))), x);
        var condition = new And(new GreaterThan(column(projected, 0), column(y, 0)),
                new LessThan(column(projected, 1), column(y, 1)));
        var plan = new Join(projected, y, LeftSemi$.MODULE$, Option.<Expression>apply(condition),
                JoinHint$.MODULE$.NONE());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.child() instanceof Join j
                && j.joinType() == LeftSemi$.MODULE$ && j.left() instanceof LocalRelation);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAntiJoinAlone() {
        var x = Support.relation("x", 2, List.of(), new Random(1));
        var y = Support.relation("y", 2, List.of(), new Random(2));
        var projected = new Project(Catalyst.seq(List.of(
                alias(sum(column(x, 0), column(x, 1)), "p0"),
                alias(product(column(x, 0), column(x, 1)), "p1"))), x);
        var condition = new And(new GreaterThan(column(projected, 0), column(y, 0)),
                new LessThan(column(projected, 1), column(y, 1)));
        var plan = new Join(projected, y, LeftAnti$.MODULE$, Option.<Expression>apply(condition),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSingleColumnProjectionAlone() {
        var x = Support.relation("x", 2, List.of(), new Random(1));
        var y = Support.relation("y", 2, List.of(), new Random(2));
        var projected = new Project(Catalyst.seq(List.of(
                alias(sum(column(x, 0), column(x, 1)), "p0"))), x);
        var condition = new GreaterThan(column(projected, 0), column(y, 0));
        var plan = new Join(projected, y, LeftSemi$.MODULE$, Option.<Expression>apply(condition),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}

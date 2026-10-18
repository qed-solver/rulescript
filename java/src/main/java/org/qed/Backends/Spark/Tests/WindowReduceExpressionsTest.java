package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowReduceExpressionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("WindowReduceExpressions.json"));

    private static Alias alias(Expression child, String name) {
        return Alias$.MODULE$.apply(child, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Aggregate perKey(LogicalPlan base, Attribute k, Expression operand) {
        var x = alias(operand, "x");
        var proj = new Project(Catalyst.<NamedExpression>seq(List.of(k, x)), base);
        var max = AggregateExpression$.MODULE$.apply(new Max(x.toAttribute()), Complete$.MODULE$, false,
                scala.Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var f = alias(max, "f");
        return new Aggregate(Catalyst.<Expression>seq(List.of(k)), Catalyst.<NamedExpression>seq(List.of(alias(k, "k"), f)),
                proj, scala.Option.<AggregateHint>empty());
    }

    private static Join window(LogicalPlan base, Attribute k, Aggregate agg, JoinType type) {
        var aggKey = Catalyst.<Attribute>list(agg.output()).get(0);
        return new Join(base, agg, type, scala.Option.<Expression>apply(new EqualTo(k, aggKey)),
                JoinHint$.MODULE$.NONE());
    }

    @Test
    void rewritesWindowOverFalseAndPredicateToFalse() {
        var base = Support.relation("b", 2, List.of(), new Random(3));
        var k = Catalyst.<Attribute>list(base.output()).get(0);
        var v = Catalyst.<Attribute>list(base.output()).get(1);
        var operand = new And(Literal$.MODULE$.apply(Boolean.FALSE), new GreaterThan(v, Literal$.MODULE$.apply(2)));
        var plan = window(base, k, perKey(base, k, operand), Inner$.MODULE$);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var join = assertInstanceOf(Join.class, rewritten instanceof Project restored ? restored.child() : rewritten);
        assertEquals(base, join.left());
        var agg = assertInstanceOf(Aggregate.class, join.right());
        var project = assertInstanceOf(Project.class, agg.child());
        assertEquals(base, project.child());
        assertTrue(project.projectList().apply(1) instanceof Alias a
                && a.child() instanceof Literal l && Boolean.FALSE.equals(l.value()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterWindowAlone() {
        var base = Support.relation("b", 2, List.of(), new Random(3));
        var k = Catalyst.<Attribute>list(base.output()).get(0);
        var v = Catalyst.<Attribute>list(base.output()).get(1);
        var operand = new And(Literal$.MODULE$.apply(Boolean.FALSE), new GreaterThan(v, Literal$.MODULE$.apply(2)));
        var plan = window(base, k, perKey(base, k, operand), LeftOuter$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnOrWindowAlone() {
        var base = Support.relation("b", 2, List.of(), new Random(3));
        var k = Catalyst.<Attribute>list(base.output()).get(0);
        var v = Catalyst.<Attribute>list(base.output()).get(1);
        var operand = new Or(Literal$.MODULE$.apply(Boolean.FALSE), new GreaterThan(v, Literal$.MODULE$.apply(2)));
        var plan = window(base, k, perKey(base, k, operand), Inner$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }
}

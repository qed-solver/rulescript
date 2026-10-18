package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushSelectIntoInlinableProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushSelectIntoInlinableProject.json"));

    private static Alias named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void pushesBothFiltersBelowTheProject() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var f = named(new Add(a, b), "f");
        var g = named(new Multiply(a, b), "g");
        var project = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(f, g, a, b)), base);
        var condition = new And(new GreaterThan(f.toAttribute(), Literal$.MODULE$.apply(2)),
                new LessThan(g.toAttribute(), Literal$.MODULE$.apply(15)));
        var plan = new Filter(condition, project);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        assertEquals(List.of(f.exprId(), g.exprId(), a.exprId(), b.exprId()),
                Catalyst.<NamedExpression>list(top.projectList()).stream().map(NamedExpression::exprId).toList());
        assertTrue(top.child() instanceof Filter);
        var inner = (Filter) top.child();
        assertTrue(inner.child() == base);
        var expectedCondition = new And(new GreaterThan(new Add(a, b), Literal$.MODULE$.apply(2)),
                new LessThan(new Multiply(a, b), Literal$.MODULE$.apply(15)));
        assertEquals(expectedCondition, inner.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnOrOfTheProjectedColumnsAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var f = named(new Add(a, b), "f");
        var g = named(new Multiply(a, b), "g");
        var project = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(f, g, a, b)), base);
        var plan = new Filter(new Or(new GreaterThan(f.toAttribute(), Literal$.MODULE$.apply(2)),
                new LessThan(g.toAttribute(), Literal$.MODULE$.apply(15))), project);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAConjunctOnAPassThroughColumnAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var f = named(new Add(a, b), "f");
        var g = named(new Multiply(a, b), "g");
        var project = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(f, g, a, b)), base);
        var plan = new Filter(new And(new GreaterThan(f.toAttribute(), Literal$.MODULE$.apply(2)),
                new LessThan(a, Literal$.MODULE$.apply(15))), project);
        assertEquals(plan, rule.apply(plan));
    }
}

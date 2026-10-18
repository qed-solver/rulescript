package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import scala.Option;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushSelectIntoProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushSelectIntoProject.json"));

    private static Alias alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression literal(int value) {
        return Literal$.MODULE$.apply(value);
    }

    private static Project synthesized(LocalRelation base, Attribute a, Alias f) {
        return new Project(Catalyst.<NamedExpression>seq(List.of(f, a)), base);
    }

    @Test
    void pushesBoundConjunctBelowProject() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        var f = alias(Multiply$.MODULE$.apply(a, a, Multiply$.MODULE$.apply$default$3()), "f");
        var project = synthesized(base, a, f);
        var fAttr = (f).toAttribute();
        var plan = new Filter(new And(new GreaterThan(a, literal(1)), new LessThan(fAttr, literal(10))), project);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter);
        var top = (Filter) rewritten;
        assertTrue(top.child() instanceof Project);
        var proj = (Project) top.child();
        assertTrue(proj.child() instanceof Filter);
        var inner = (Filter) proj.child();
        assertEquals(base, inner.child());
        assertEquals(new LessThan(fAttr, literal(10)), top.condition());
        assertEquals(new GreaterThan(a, literal(1)), inner.condition());
        assertEquals(Catalyst.list(project.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesDisjunctionAboveProjectAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        var f = alias(Multiply$.MODULE$.apply(a, a, Multiply$.MODULE$.apply$default$3()), "f");
        var project = synthesized(base, a, f);
        var fAttr = (f).toAttribute();
        var plan = new Filter(new Or(new GreaterThan(a, literal(1)), new LessThan(fAttr, literal(10))), project);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesConjunctOnSynthesizedColumnOnlyAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        var f = alias(Multiply$.MODULE$.apply(a, a, Multiply$.MODULE$.apply$default$3()), "f");
        var project = synthesized(base, a, f);
        var fAttr = (f).toAttribute();
        var plan = new Filter(new And(new GreaterThan(fAttr, literal(1)), new LessThan(fAttr, literal(10))), project);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesConjunctsOnPassthroughColumnAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        var f = alias(Multiply$.MODULE$.apply(a, a, Multiply$.MODULE$.apply$default$3()), "f");
        var project = synthesized(base, a, f);
        var plan = new Filter(new And(new GreaterThan(a, literal(1)), new LessThan(a, literal(3))), project);
        assertEquals(plan, rule.apply(plan));
    }
}

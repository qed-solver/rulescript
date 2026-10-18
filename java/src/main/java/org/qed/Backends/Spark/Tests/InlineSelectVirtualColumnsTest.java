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
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InlineSelectVirtualColumnsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InlineSelectVirtualColumns.json"));

    private static Alias alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void inlinesVirtualColumnFilterBelowProject() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var v = alias(new Add(a, b), "v");
        var w = alias(new Add(b, Literal$.MODULE$.apply(1)), "w");
        var project = new Project(Catalyst.<NamedExpression>seq(List.of(v, w, a, b)), base);
        var vOut = v.toAttribute();
        var wOut = w.toAttribute();
        var plan = new Filter(new And(new GreaterThan(vOut, Literal$.MODULE$.apply(2)),
                new LessThan(wOut, Literal$.MODULE$.apply(3))), project);

        LogicalPlan rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var top = assertInstanceOf(Filter.class, rewritten);
        assertEquals(new LessThan(wOut, Literal$.MODULE$.apply(3)), top.condition());
        var middle = assertInstanceOf(Project.class, top.child());
        assertEquals(Catalyst.list(project.projectList()), Catalyst.list(middle.projectList()));
        var inner = assertInstanceOf(Filter.class, middle.child());
        assertEquals(new GreaterThan(new Add(a, b), Literal$.MODULE$.apply(2)), inner.condition());
        assertEquals(base, inner.child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectWithoutPassthroughColumnsAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var v = alias(new Add(a, b), "v");
        var w = alias(new Add(b, Literal$.MODULE$.apply(1)), "w");
        var project = new Project(Catalyst.<NamedExpression>seq(List.of(v, w, a)), base);
        var plan = new Filter(new And(new GreaterThan(v.toAttribute(), Literal$.MODULE$.apply(2)),
                new LessThan(w.toAttribute(), Literal$.MODULE$.apply(3))), project);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var v = alias(new Add(a, b), "v");
        var w = alias(new Add(b, Literal$.MODULE$.apply(1)), "w");
        var project = new Project(Catalyst.<NamedExpression>seq(List.of(v, w, a, b)), base);
        var plan = new Filter(new Or(new GreaterThan(v.toAttribute(), Literal$.MODULE$.apply(2)),
                new LessThan(w.toAttribute(), Literal$.MODULE$.apply(3))), project);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void inlinesWhenTheScanIsAFilteredRelation() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var v = alias(new Add(a, b), "v");
        var w = alias(new Add(b, Literal$.MODULE$.apply(1)), "w");
        var below = new Filter(new GreaterThan(a, Literal$.MODULE$.apply(0)), base);
        var project = new Project(Catalyst.<NamedExpression>seq(List.of(v, w, a, b)), below);
        var plan = new Filter(new And(new GreaterThan(v.toAttribute(), Literal$.MODULE$.apply(2)),
                new LessThan(w.toAttribute(), Literal$.MODULE$.apply(3))), project);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }
}

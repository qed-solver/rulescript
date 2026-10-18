package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Subtract;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMergeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectMerge.json"));

    private static Expression alias(Expression child, String name) {
        return Alias$.MODULE$.apply(child, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void mergesStackedProjects() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(
                (NamedExpression) alias(new Add(x, Literal$.MODULE$.apply(2)), "y"))), base);
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                (NamedExpression) alias(new Subtract(y, Literal$.MODULE$.apply(1)), "z"))), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var merged = assertInstanceOf(Project.class, rewritten);
        assertEquals(base, merged.child());
        var item = Catalyst.<NamedExpression>list(merged.projectList()).get(0);
        var top = assertInstanceOf(Subtract.class, assertInstanceOf(Alias.class, item).child());
        var bottom = assertInstanceOf(Add.class, top.left());
        assertEquals(x.exprId(), assertInstanceOf(Attribute.class, bottom.left()).exprId());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAProjectOverAFilterAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var filtered = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                (NamedExpression) alias(new Subtract(x, Literal$.MODULE$.apply(1)), "z"))), filtered);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterProjectTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterProjectTranspose.json"));

    private static Alias projection(org.apache.spark.sql.catalyst.expressions.Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void transposesFilterBelowSingleColumnProject() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var sum = new Add(x, Literal$.MODULE$.apply(1));
        var column = projection(sum, "p");
        var project = new Project(Catalyst.<NamedExpression>seq(List.of(column)), base);
        var plan = new Filter(new GreaterThan(column.toAttribute(), Literal$.MODULE$.apply(2)), project);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        assertTrue(top.child() instanceof Filter);
        var below = (Filter) top.child();
        assertEquals(base, below.child());
        assertEquals(new GreaterThan(sum, Literal$.MODULE$.apply(2)), below.condition());
        assertEquals(List.of(column), Catalyst.<NamedExpression>list(top.projectList()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterAloneWhenProjectHasTwoColumns() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var column = projection(new Add(x, Literal$.MODULE$.apply(1)), "p");
        var project = new Project(Catalyst.<NamedExpression>seq(List.of(column, x)), base);
        var plan = new Filter(new GreaterThan(column.toAttribute(), Literal$.MODULE$.apply(2)), project);
        assertEquals(plan, rule.apply(plan));
    }
}

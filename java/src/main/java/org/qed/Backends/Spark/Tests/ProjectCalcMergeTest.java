package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Multiply;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Subtract;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
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

class ProjectCalcMergeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectCalcMerge.json"));

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void mergesProjectOfCalcIntoOneProject() {
        var base = Support.relation("s", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var x = cols.get(0);
        var y = cols.get(1);
        var filter = new Filter(new GreaterThan(x, y), base);
        var bottom = new Project(Catalyst.<NamedExpression>seq(List.of(
                named(new Add(x, y), "e0"), named(new Subtract(x, y), "e1"))), filter);
        var e0 = Catalyst.<Attribute>list(bottom.output()).get(0);
        var e1 = Catalyst.<Attribute>list(bottom.output()).get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                named(new Add(e0, e1), "p0"), named(new Multiply(e0, e1), "p1"))), bottom);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.child() instanceof Filter f && f.child() == base);
        var items = Catalyst.<NamedExpression>list(((Project) rewritten).projectList());
        assertEquals(2, items.size());
        assertEquals(new Add(new Add(x, y), new Subtract(x, y)), ((Alias) items.get(0)).child());
        assertEquals(new Multiply(new Add(x, y), new Subtract(x, y)), ((Alias) items.get(1)).child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectOverCalcOfOtherWidthAlone() {
        var base = Support.relation("s", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var x = cols.get(0);
        var y = cols.get(1);
        var filter = new Filter(new GreaterThan(x, y), base);
        var bottom = new Project(Catalyst.<NamedExpression>seq(List.of(
                named(new Add(x, y), "e0"), named(new Subtract(x, y), "e1"))), filter);
        var e0 = Catalyst.<Attribute>list(bottom.output()).get(0);
        var e1 = Catalyst.<Attribute>list(bottom.output()).get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                named(new Add(e0, e1), "p0"), named(new Multiply(e0, e1), "p1"))), bottom);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectReadingBelowTheCalcAlone() {
        var base = Support.relation("s", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var x = cols.get(0);
        var y = cols.get(1);
        var filter = new Filter(new GreaterThan(x, y), base);
        var bottom = new Project(Catalyst.<NamedExpression>seq(List.of(
                named(new Add(x, y), "e0"), named(new Subtract(x, y), "e1"))), filter);
        var e0 = Catalyst.<Attribute>list(bottom.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                named(new Add(e0, x), "p0"), named(new Multiply(e0, y), "p1"))), bottom);
        assertEquals(plan, rule.apply(plan));
    }
}

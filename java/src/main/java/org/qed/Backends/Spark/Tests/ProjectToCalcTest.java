package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProjectToCalcTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectToCalc.json"));

    private static NamedExpression alias(String name, Expression value) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void projectOfTwoExpressionsGetsATrueFilterUnderneath() {
        var base = Support.relation("s", 2, List.of(), new Random(3));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.of(
                alias("first", new GreaterThan(cols.get(0), cols.get(1))),
                alias("second", new EqualTo(cols.get(0), cols.get(1))))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var project = assertInstanceOf(Project.class, rewritten);
        var filter = assertInstanceOf(Filter.class, project.child());
        assertEquals(Literal$.MODULE$.TrueLiteral(), filter.condition());
        assertSame(base, filter.child());
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void singleExpressionProjectIsLeftAlone() {
        var base = Support.relation("s", 2, List.of(), new Random(3));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.of(
                alias("first", new GreaterThan(cols.get(0), cols.get(1))))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void projectOverThreeColumnsIsLeftAlone() {
        var base = Support.relation("s", 3, List.of(), new Random(3));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.of(
                alias("first", new GreaterThan(cols.get(0), cols.get(1))),
                alias("second", new EqualTo(cols.get(0), cols.get(1))))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

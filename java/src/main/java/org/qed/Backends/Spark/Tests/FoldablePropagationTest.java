package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FoldablePropagationTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldablePropagation.json"));

    private static Project project(LogicalPlan base, Attribute x, Expression value) {
        var alias = Alias$.MODULE$.apply(value, "f", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
        return new Project(Catalyst.<NamedExpression>seq(List.of(x, alias)), base);
    }

    @Test
    void substitutesTheAliasWithTheFoldableConstant() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var projected = project(base, x, Literal$.MODULE$.apply(3));
        var alias = Catalyst.<Attribute>list(projected.output()).get(1);
        var plan = new Filter(new GreaterThan(alias, x), projected);
        var expected = new Filter(new GreaterThan(Literal$.MODULE$.apply(3), x), projected);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAPredicateThatDoesNotReferenceTheAliasAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var projected = project(base, x, Literal$.MODULE$.apply(3));
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), projected);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectionThatReadsTheInputAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var projected = project(base, x, new IsNull(x));
        var alias = Catalyst.<Attribute>list(projected.output()).get(1);
        var plan = new Filter(new GreaterThan(alias, x), projected);
        assertEquals(plan, rule.apply(plan));
    }
}

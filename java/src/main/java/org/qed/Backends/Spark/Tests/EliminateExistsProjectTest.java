package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
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

class EliminateExistsProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateExistsProject.json"));

    private static Exists exists(LogicalPlan plan) {
        return new Exists(plan, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), Option.empty());
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void dropsTheProjectUnderExists() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var input = Support.relation("input", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(input.output()).get(0);
        var projected = new Project(Catalyst.seq(List.of(alias(new IsNull(x), "f"))), input);
        var plan = new Filter(exists(projected), outer);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == outer);
        var cond = ((Filter) rewritten).condition();
        assertTrue(cond instanceof Exists e && e.plan() instanceof LocalRelation);
        assertEquals(input.output(), ((Exists) cond).plan().output());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsExistsOverAFilterAndNoProjectAlone() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var input = Support.relation("input", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(input.output()).get(0);
        var filtered = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), input);
        var overFilter = new Filter(exists(filtered), outer);
        assertEquals(overFilter, rule.apply(overFilter));

        var y = Catalyst.<Attribute>list(outer.output()).get(0);
        var projectNotUnderExists = new Filter(new GreaterThan(y, Literal$.MODULE$.apply(2)),
                new Project(Catalyst.seq(List.of(alias(new IsNull(y), "f"))), outer));
        assertEquals(projectNotUnderExists, rule.apply(projectNotUnderExists));
    }
}

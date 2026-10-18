package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.UnaryMinus$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RejectNullsProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RejectNullsProject.json"));

    private static Project negatedProject(Attribute a, org.apache.spark.sql.catalyst.plans.logical.LogicalPlan child) {
        var negated = Alias$.MODULE$.apply(UnaryMinus$.MODULE$.apply(a, UnaryMinus$.MODULE$.apply$default$2()), "c",
                NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()), Option.empty(),
                Catalyst.<String>seq(List.of()));
        return new Project(Catalyst.<NamedExpression>seq(List.of(negated, a)), child);
    }

    @Test
    void pushesNotNullBelowProjectWhenParentRejectsSynthesizedColumn() {
        var base = Support.relation("Source", 1, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        var project = negatedProject(a, base);
        var synthesized = Catalyst.<Attribute>list(project.output()).get(0);
        var plan = new Filter(new IsNotNull(synthesized), project);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project restore && restore.child() instanceof Filter top
                && top.condition() instanceof IsNotNull && top.child() instanceof Project p
                && p.child() instanceof Filter inner && inner.child().equals(base)
                && inner.condition() instanceof IsNotNull notNull && notNull.child().equals(a));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterAloneWhenItTestsNullInsteadOfNotNull() {
        var base = Support.relation("Source", 1, List.of(), new Random(2));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        var project = negatedProject(a, base);
        var synthesized = Catalyst.<Attribute>list(project.output()).get(0);
        var plan = new Filter(new IsNull(synthesized), project);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterAloneWhenItTestsPassthroughColumn() {
        var base = Support.relation("Source", 1, List.of(), new Random(3));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        var project = negatedProject(a, base);
        var passthrough = Catalyst.<Attribute>list(project.output()).get(1);
        var plan = new Filter(new IsNotNull(passthrough), project);
        assertEquals(plan, rule.apply(plan));
    }
}

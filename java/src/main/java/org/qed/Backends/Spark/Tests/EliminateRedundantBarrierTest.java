package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateRedundantBarrierTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateRedundantBarrier.json"));

    @Test
    void collapsesNestedIdentityProjects() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        var innerOut = Catalyst.<Attribute>list(inner.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(innerOut)), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof LocalRelation);
        assertEquals(List.of(x), Catalyst.<NamedExpression>list(((Project) rewritten).projectList()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesASingleProjectAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectsSeparatedByFilterAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        var filter = new Filter(new GreaterThan(Catalyst.<Attribute>list(inner.output()).get(0), Literal$.MODULE$.apply(2)), inner);
        var outerOut = Catalyst.<Attribute>list(filter.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(outerOut)), filter);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAFilterOnTopOfAProjectAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        var innerOut = Catalyst.<Attribute>list(inner.output()).get(0);
        var plan = new Filter(new GreaterThan(innerOut, Literal$.MODULE$.apply(2)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAProjectOfATwoColumnInputAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(attrs.get(0))), base);
        var innerOut = Catalyst.<Attribute>list(inner.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(innerOut)), inner);
        assertEquals(plan, rule.apply(plan));
    }
}

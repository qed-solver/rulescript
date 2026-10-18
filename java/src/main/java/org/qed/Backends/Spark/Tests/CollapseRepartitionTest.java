package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollapseRepartitionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CollapseRepartition.json"));

    @Test
    void collapsesStackedIdentityProjects() {
        var base = Support.relation("s", 3, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Project(Catalyst.<NamedExpression>seq(attrs), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(Catalyst.<Attribute>list(inner.output())), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() == base);
        assertEquals(attrs, Catalyst.<NamedExpression>list(((Project) rewritten).projectList()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAReorderingInnerProjectAlone() {
        var base = Support.relation("s", 3, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var reordered = List.of(attrs.get(1), attrs.get(0), attrs.get(2));
        var inner = new Project(Catalyst.<NamedExpression>seq(reordered), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(Catalyst.<Attribute>list(inner.output())), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAFilterBetweenProjectsAlone() {
        var base = Support.relation("s", 3, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Project(Catalyst.<NamedExpression>seq(attrs), base);
        var filtered = new Filter(new GreaterThan(attrs.get(0), Literal$.MODULE$.apply(2)), inner);
        var plan = new Project(Catalyst.<NamedExpression>seq(Catalyst.<Attribute>list(filtered.output())), filtered);
        assertEquals(plan, rule.apply(plan));
    }
}

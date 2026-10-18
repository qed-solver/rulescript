package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EliminateProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateProject.json"));

    @Test
    void removesIdentityProjectOverTwoColumns() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(cols.get(0), cols.get(1))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertSame(base, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsProjectWithSwappedColumns() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(cols.get(1), cols.get(0))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsProjectThatDropsAColumn() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(cols.get(0))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsProjectThatDropsTheLastOfThreeColumns() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.<NamedExpression>of(cols.get(0), cols.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

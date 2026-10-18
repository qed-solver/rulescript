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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalcRemoveTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CalcRemove.json"));

    @Test
    void removesIdentityProjectOverTwoColumns() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), cols.get(1))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(base, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsProjectThatSwapsColumns() {
        var base = Support.relation("t", 2, List.of(), new Random(2));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(1), cols.get(0))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsIdentityProjectOverThreeColumns() {
        var base = Support.relation("t", 3, List.of(), new Random(3));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), cols.get(1), cols.get(2))), base);
        assertTrue(rule.apply(plan).equals(plan));
    }
}

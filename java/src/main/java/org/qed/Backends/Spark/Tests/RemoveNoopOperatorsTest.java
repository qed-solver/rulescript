package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
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

class RemoveNoopOperatorsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RemoveNoopOperators.json"));

    @Test
    void dropsIdentityProjectOverSingleColumnInput() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.seq(List.of(x)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation);
        assertEquals(base, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void dropsIdentityProjectOverNonNullSingleColumnInput() {
        var base = Support.relation("t", 1, List.of(0), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.seq(List.of(x)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(base, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsProjectThatSelectsOneOfTwoColumns() {
        var base = Support.relation("t", 2, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.seq(List.of(x)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsProjectThatSelectsSecondOfTwoColumns() {
        var base = Support.relation("t", 2, List.of(), new Random(4));
        var y = Catalyst.<Attribute>list(base.output()).get(1);
        var plan = new Project(Catalyst.seq(List.of(y)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void dropsIdentityProjectOverFilterOfSingleColumn() {
        var base = Support.relation("t", 1, List.of(), new Random(5));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var filtered = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Project(Catalyst.seq(List.of(x)), filtered);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(filtered, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }
}

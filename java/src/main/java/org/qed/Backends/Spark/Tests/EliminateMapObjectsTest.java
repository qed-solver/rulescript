package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateMapObjectsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateMapObjects.json"));

    @Test
    void identityProjectionOfNonNullColumnIsRemoved() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation);
        assertEquals(Catalyst.list(base.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void identityProjectionOfNullableColumnIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void projectionOfTwoColumnsIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoveNotNullConditionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RemoveNotNullCondition.json"));

    @Test
    void removesIsNotNullOnNonNullColumn() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var other = new GreaterThan(attrs.get(1), Literal$.MODULE$.apply(2));
        var inner = new Filter(other, base);
        var plan = new Filter(new IsNotNull(attrs.get(0)), inner);
        var expected = new Filter(other, base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsIsNotNullOnNullableColumn() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Filter(new GreaterThan(attrs.get(1), Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(new IsNotNull(attrs.get(0)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsIsNullOnNonNullColumn() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Filter(new GreaterThan(attrs.get(1), Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(new IsNull(attrs.get(0)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsIsNotNullWithoutStackedFilter() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new IsNotNull(attrs.get(0)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

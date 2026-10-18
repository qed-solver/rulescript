package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyIsConditionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyIsCondition.json"));

    @Test
    void replacesNullSafeEqualityWithEqualityWhenLeftIsNonNull() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var inner = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), base);
        var plan = new Filter(new EqualNullSafe(x, y), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter, "result should be a filter");
        var outer = (Filter) rewritten;
        assertTrue(outer.condition() instanceof EqualTo, "condition should be an equality");
        assertEquals(new EqualTo(x, y), outer.condition());
        assertTrue(outer.child() instanceof Filter, "the other filter should be kept below");
        assertEquals(inner.condition(), ((Filter) outer.child()).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsNullSafeEqualityWhenLeftIsNullable() {
        var base = Support.relation("t", 2, List.of(), new Random(2));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Filter(new GreaterThan(attrs.get(0), Literal$.MODULE$.apply(1)), base);
        var plan = new Filter(new EqualNullSafe(attrs.get(0), attrs.get(1)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsNullSafeEqualityWhenOnlyTheRightOperandIsNonNull() {
        var base = Support.relation("t", 2, List.of(1), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Filter(new GreaterThan(attrs.get(0), Literal$.MODULE$.apply(1)), base);
        var plan = new Filter(new EqualNullSafe(attrs.get(0), attrs.get(1)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsPlainEqualityBecauseItIsNotNullSafe() {
        var base = Support.relation("t", 2, List.of(0), new Random(4));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Filter(new GreaterThan(attrs.get(0), Literal$.MODULE$.apply(1)), base);
        var plan = new Filter(new EqualTo(attrs.get(0), attrs.get(1)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsNullSafeEqualityWithoutTheNestedFilter() {
        var base = Support.relation("t", 2, List.of(0), new Random(5));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new EqualNullSafe(attrs.get(0), attrs.get(1)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsNullSafeEqualityWhenTheNonNullColumnIsOnTheRight() {
        var base = Support.relation("t", 2, List.of(0), new Random(6));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Filter(new LessThan(attrs.get(0), Literal$.MODULE$.apply(3)), base);
        var plan = new Filter(new EqualNullSafe(attrs.get(1), attrs.get(0)), inner);
        assertEquals(plan, rule.apply(plan));
    }
}

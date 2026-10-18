package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyIsNullConditionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyIsNullCondition.json"));

    @Test
    void isNullOnNonNullColumnBecomesFalse() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Filter(new GreaterThan(attrs.get(1), Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(new IsNull(attrs.get(0)), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(Literal$.MODULE$.FalseLiteral(), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void isNullOnNullableColumnIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Filter(new GreaterThan(attrs.get(0), Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(new IsNull(attrs.get(1)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void isNotNullOnNonNullColumnIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var inner = new Filter(new GreaterThan(attrs.get(1), Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(new Not(new IsNull(attrs.get(0))), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void singleFilterWithoutNestingIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new IsNull(attrs.get(0)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

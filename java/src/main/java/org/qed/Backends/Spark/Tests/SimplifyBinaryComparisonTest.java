package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyBinaryComparisonTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyBinaryComparison.json"));

    @Test
    void rewritesSelfEqualityOnNonNullColumnToTrue() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(x, x), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f
                && f.condition() instanceof Literal c && Boolean.TRUE.equals(c.value())
                && f.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        var expected = Support.run(plan);
        assertFalse(expected.isEmpty());
        assertEquals(expected, Support.run(rewritten));
    }

    @Test
    void rewritesSelfEqualityOnNonNullColumnForAnotherSeed() {
        var base = Support.relation("t", 1, List.of(0), new Random(42));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(x, x), base);
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof Filter f
                && f.condition() instanceof Literal c && Boolean.TRUE.equals(c.value())
                && f.child() == base);
    }

    @Test
    void leavesSelfEqualityOnNullableColumnAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(x, x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesEqualityOfTwoDifferentColumnsAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new EqualTo(attrs.get(0), attrs.get(1)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesStrictInequalityOfSameColumnAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesEqualityAgainstLiteralAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

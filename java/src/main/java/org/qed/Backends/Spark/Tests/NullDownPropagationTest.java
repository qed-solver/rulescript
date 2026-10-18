package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NullDownPropagationTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("NullDownPropagation.json"));

    @Test
    void propagatesIsNullBelowComparison() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var plan = new Filter(new IsNull(new LessThan(x, y)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(new Or(new IsNull(x), new IsNull(y)), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesIsNullOfGreaterThanAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(2));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new IsNull(new GreaterThan(attrs.get(0), attrs.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNotNullOfLessThanAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new IsNotNull(new LessThan(attrs.get(0), attrs.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNullOfSingleOperandAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new IsNull(x), base);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyNotInSingleElementTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyNotInSingleElement.json"));

    @Test
    void rewritesNotInSingleElementToNotEqual() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var plan = new Filter(new Not(new EqualTo(x, y)), base);
        assertTrue(rule.matches(plan));
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(new Not(new EqualTo(x, y)), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNotInWithTwoElementsAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var plan = new Filter(new Not(new Or(new EqualTo(x, y), new EqualTo(y, x))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNegatedInequalityAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var plan = new Filter(new Not(new LessThan(x, y)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesUnnegatedEqualityAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var plan = new Filter(new EqualTo(x, y), base);
        assertEquals(plan, rule.apply(plan));
    }
}

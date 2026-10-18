package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
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

class OptimizeInTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("OptimizeIn.json"));

    @Test
    void collapsesOrOfTheSameEquality() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var eq = new EqualTo(cols.get(0), cols.get(1));
        var plan = new Filter(new Or(eq, eq), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(new Filter(eq, base), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void collapsesOrOfTheSameEqualityOnNonNullColumns() {
        var base = Support.relation("u", 2, List.of(0, 1), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var eq = new EqualTo(cols.get(0), cols.get(1));
        var plan = new Filter(new Or(eq, eq), base);
        var rewritten = rule.apply(plan);
        assertEquals(new Filter(eq, base), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOrOfDifferentOperatorsAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new Or(new EqualTo(cols.get(0), cols.get(1)), new LessThan(cols.get(0), cols.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOrWithADifferentSecondDisjunctAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new Or(new EqualTo(cols.get(0), cols.get(1)), new EqualTo(cols.get(0), cols.get(0))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesASingleEqualityAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new EqualTo(cols.get(0), cols.get(1)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAWiderInputAlone() {
        var base = Support.relation("w", 3, List.of(), new Random(3));
        var cols = Catalyst.<Attribute>list(base.output());
        var eq = new EqualTo(cols.get(1), cols.get(2));
        var plan = new Filter(new Or(eq, eq), base);
        assertEquals(plan, rule.apply(plan));
    }
}

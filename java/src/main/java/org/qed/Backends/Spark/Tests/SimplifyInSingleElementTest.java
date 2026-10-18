package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyInSingleElementTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyInSingleElement.json"));

    @Test
    void singleElementInBecomesEquality() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new EqualTo(cols.get(0), cols.get(1)), base);
        assertTrue(rule.matches(plan));
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition() instanceof EqualTo);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void twoElementOrOfEqualitiesIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new Or(new EqualTo(cols.get(0), cols.get(1)), new EqualTo(cols.get(1), cols.get(0))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void differentOperatorIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new GreaterThan(cols.get(0), cols.get(1)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void equalityWithLiteralIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new EqualTo(cols.get(0), Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

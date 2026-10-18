package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
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

class TryRemapSelectOuterColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryRemapSelectOuterCols.json"));

    @Test
    void movesThePredicateOntoTheEqualColumn() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var on = new EqualTo(cols.get(0), cols.get(1));
        var plan = new Filter(on, new Filter(new GreaterThan(cols.get(0), Literal$.MODULE$.apply(2)), base));
        var expected = new Filter(on, new Filter(new GreaterThan(cols.get(1), Literal$.MODULE$.apply(2)), base));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void movesALessThanPredicateOntoTheEqualColumn() {
        var base = Support.relation("t", 3, List.of(), new Random(2));
        var cols = Catalyst.<Attribute>list(base.output());
        var on = new EqualTo(cols.get(0), cols.get(1));
        var plan = new Filter(on, new Filter(new LessThan(cols.get(0), Literal$.MODULE$.apply(3)), base));
        var expected = new Filter(on, new Filter(new LessThan(cols.get(1), Literal$.MODULE$.apply(3)), base));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesANonEqualityFilterAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(3));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new LessThan(cols.get(0), cols.get(1)),
                new Filter(new GreaterThan(cols.get(0), Literal$.MODULE$.apply(2)), base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesThePredicateOnTheOtherColumnAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(4));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new EqualTo(cols.get(0), cols.get(1)),
                new Filter(new GreaterThan(cols.get(1), Literal$.MODULE$.apply(2)), base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnEqualityWithAnUnrelatedColumnAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(5));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new EqualTo(cols.get(0), cols.get(2)),
                new Filter(new GreaterThan(cols.get(0), Literal$.MODULE$.apply(2)), base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesASingleFilterAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(6));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new EqualTo(cols.get(0), cols.get(1)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

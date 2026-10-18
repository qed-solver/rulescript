package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
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

class DeduplicateSelectFiltersTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("DeduplicateSelectFilters.json"));

    @Test
    void dropsTheOuterCopyOfTheDuplicatedPredicate() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var q = new LessThan(x, Literal$.MODULE$.apply(15));
        var plan = new Filter(p, new Filter(q, new Filter(p, base)));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(new Filter(q, new Filter(p, base)), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesADifferentPredicateInTheDuplicatePositionAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var q = new LessThan(x, Literal$.MODULE$.apply(15));
        var other = new GreaterThan(x, Literal$.MODULE$.apply(3));
        var plan = new Filter(p, new Filter(q, new Filter(other, base)));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoFiltersAloneEvenWhenTheyRepeat() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(p, new Filter(p, base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesADuplicateThatIsNotOutermostAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var q = new LessThan(x, Literal$.MODULE$.apply(15));
        var plan = new Filter(q, new Filter(p, new Filter(p, base)));
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneFiltersTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneFilters.json"));

    @Test
    void dropsTheSharedConjunctFromTheOuterFilter() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var shared = new GreaterThan(x, Literal$.MODULE$.apply(0));
        var restInner = new LessThan(x, Literal$.MODULE$.apply(3));
        var restOuter = new LessThan(x, Literal$.MODULE$.apply(2));
        var inner = new Filter(new And(shared, restInner), base);
        var plan = new Filter(new And(shared, restOuter), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter);
        var outer = (Filter) rewritten;
        assertEquals(restOuter, outer.condition());
        assertTrue(outer.child() instanceof Filter);
        var kept = (Filter) outer.child();
        assertEquals(new And(shared, restInner), kept.condition());
        assertEquals(base, kept.child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFiltersAloneWhenTheOuterConjunctDiffers() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var shared = new GreaterThan(x, Literal$.MODULE$.apply(0));
        var otherShared = new GreaterThan(x, Literal$.MODULE$.apply(1));
        var restInner = new LessThan(x, Literal$.MODULE$.apply(3));
        var restOuter = new LessThan(x, Literal$.MODULE$.apply(2));
        var inner = new Filter(new And(shared, restInner), base);
        var plan = new Filter(new And(otherShared, restOuter), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesASingleFilterAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var shared = new GreaterThan(x, Literal$.MODULE$.apply(0));
        var restInner = new LessThan(x, Literal$.MODULE$.apply(3));
        var plan = new Filter(new And(shared, restInner), base);
        assertEquals(plan, rule.apply(plan));
    }
}

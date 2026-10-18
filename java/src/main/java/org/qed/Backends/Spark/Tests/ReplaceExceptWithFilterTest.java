package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Except;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaceExceptWithFilterTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ReplaceExceptWithFilter.json"));

    @Test
    void replacesExceptWithFilterAndIntersect() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), base);
        var right = new Filter(new LessThan(x, Literal$.MODULE$.apply(3)), base);
        var plan = new Except(left, right, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Intersect);
        var intersect = (Intersect) rewritten;
        assertEquals(false, intersect.isAll());
        assertTrue(intersect.left() instanceof Filter);
        var outer = (Filter) intersect.left();
        assertTrue(outer.child() instanceof Filter);
        assertTrue(((Filter) outer.child()).child() == base);
        assertEquals(intersect.left(), intersect.right());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesExceptOnDifferentBaseAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var other = Support.relation("u", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var y = Catalyst.<Attribute>list(other.output()).get(0);
        var left = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), base);
        var right = new Filter(new LessThan(y, Literal$.MODULE$.apply(3)), other);
        var plan = new Except(left, right, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesPlainFilterAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

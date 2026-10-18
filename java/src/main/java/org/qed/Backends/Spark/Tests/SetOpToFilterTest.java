package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Distinct;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetOpToFilterTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SetOpToFilter.json"));

    @Test
    void distinctUnionOfTwoFiltersOverOneSourceBecomesFilterWithOr() {
        var base = Support.relation("t", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var right = new Filter(new LessThan(x, Literal$.MODULE$.apply(1)), base);
        List<LogicalPlan> kids = List.of(left, right);
        var plan = new Distinct(new Union(Catalyst.<LogicalPlan>seq(kids), false, false));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate a && a.child() instanceof Filter f
                && f.child() == base && f.condition() instanceof Or);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void plainUnionAllOfTwoFiltersIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var right = new Filter(new LessThan(x, Literal$.MODULE$.apply(1)), base);
        List<LogicalPlan> kids = List.of(left, right);
        var plan = new Union(Catalyst.<LogicalPlan>seq(kids), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void distinctUnionOfFiltersOverDifferentSourcesIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(7));
        var other = Support.relation("u", 1, List.of(), new Random(8));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var y = Catalyst.<Attribute>list(other.output()).get(0);
        var left = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var right = new Filter(new LessThan(y, Literal$.MODULE$.apply(1)), other);
        List<LogicalPlan> kids = List.of(left, right);
        var plan = new Distinct(new Union(Catalyst.<LogicalPlan>seq(kids), false, false));
        assertEquals(plan, rule.apply(plan));
    }
}

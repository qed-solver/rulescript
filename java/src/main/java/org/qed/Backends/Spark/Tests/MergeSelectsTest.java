package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeSelectsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("MergeSelects.json"));

    @Test
    void mergesStackedFiltersIntoOneConjunction() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(new LessThan(x, Literal$.MODULE$.apply(15)), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition() instanceof And);
        var condition = (And) ((Filter) rewritten).condition();
        assertEquals(new GreaterThan(x, Literal$.MODULE$.apply(2)), condition.left());
        assertEquals(new LessThan(x, Literal$.MODULE$.apply(15)), condition.right());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFiltersSeparatedByAProjectAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var projected = new Project(Catalyst.seq(List.of(x)), inner);
        var plan = new Filter(new LessThan(x, Literal$.MODULE$.apply(15)), projected);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesASingleFilterAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FilterMergeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterMerge.json"));

    @Test
    void mergesStackedFiltersIntoOneConjunction() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var outer = new LessThan(x, Literal$.MODULE$.apply(15));
        var plan = new Filter(outer, new Filter(inner, base));
        var expected = new Filter(new And(inner, outer), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesASingleFilterAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFiltersSeparatedByAProjectAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var outer = new LessThan(x, Literal$.MODULE$.apply(15));
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(x)), new Filter(inner, base));
        var plan = new Filter(outer, projected);
        assertEquals(plan, rule.apply(plan));
    }
}

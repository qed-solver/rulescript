package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertGroupByToDistinctTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ConvertGroupByToDistinct.json"));

    @Test
    void groupByBothColumnsBecomesIntersect() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(attrs.get(0), attrs.get(1))),
                Catalyst.<NamedExpression>seq(List.of(attrs.get(0), attrs.get(1))), base,
                scala.Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Intersect i && !i.isAll() && i.left().equals(base)
                && i.right() instanceof LocalRelation r && r.output().size() == 2);
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void groupByOnlyOneColumnIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(attrs.get(0))),
                Catalyst.<NamedExpression>seq(List.of(attrs.get(0))), base,
                scala.Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nullableGroupingColumnsAreLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(attrs.get(0), attrs.get(1))),
                Catalyst.<NamedExpression>seq(List.of(attrs.get(0), attrs.get(1))), base,
                scala.Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }
}

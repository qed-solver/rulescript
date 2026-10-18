package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Count;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateExpandDistinctAggregatesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateExpandDistinctAggregates.json"));

    private static Alias named(org.apache.spark.sql.catalyst.expressions.Expression child, String name) {
        return (Alias) Alias$.MODULE$.apply(child, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Alias distinctCount(Attribute x, String name) {
        return named(AggregateExpression$.MODULE$.apply(new Count(Catalyst.seq(List.of(x))), Complete$.MODULE$,
                true, scala.Option.empty(), NamedExpression$.MODULE$.newExprId()), name);
    }

    private static Alias distinctMax(Attribute x, String name) {
        return named(AggregateExpression$.MODULE$.apply(new Max(x), Complete$.MODULE$,
                true, scala.Option.empty(), NamedExpression$.MODULE$.newExprId()), name);
    }

    private static Alias plainMax(Attribute x, String name) {
        return named(AggregateExpression$.MODULE$.apply(new Max(x), Complete$.MODULE$,
                false, scala.Option.empty(), NamedExpression$.MODULE$.newExprId()), name);
    }

    private static Aggregate aggregate(Attribute key, List<Alias> aggs, LogicalPlan child) {
        var items = new java.util.ArrayList<org.apache.spark.sql.catalyst.expressions.NamedExpression>();
        items.add(key);
        items.addAll(aggs);
        return new Aggregate(Catalyst.seq(List.of(key)), Catalyst.seq(items), child,
                scala.Option.empty());
    }

    @Test
    void expandsTwoDistinctAggregatesOverOneArgument() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregate(x, List.of(distinctCount(x, "f"), distinctMax(x, "g")), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var outer = (Aggregate) rewritten;
        assertTrue(outer.child() instanceof Aggregate);
        var inner = (Aggregate) outer.child();
        assertEquals(2, Catalyst.list(inner.groupingExpressions()).size());
        assertEquals(1, Catalyst.list(outer.groupingExpressions()).size());
        var outerItems = Catalyst.<org.apache.spark.sql.catalyst.expressions.NamedExpression>list(outer.aggregateExpressions());
        assertEquals(3, outerItems.size());
        for (var item : outerItems.subList(1, 3)) {
            assertTrue(((Alias) item).child() instanceof AggregateExpression ae && !ae.isDistinct());
        }
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnAggregateWithOneDistinctCallAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregate(x, List.of(distinctCount(x, "f")), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAMixedDistinctAndPlainAggregateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregate(x, List.of(distinctCount(x, "f"), plainMax(x, "g")), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAggregateOverTwoColumnsAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregate(x, List.of(distinctCount(x, "f"), distinctMax(x, "g")), base);
        assertEquals(plan, rule.apply(plan));
    }
}

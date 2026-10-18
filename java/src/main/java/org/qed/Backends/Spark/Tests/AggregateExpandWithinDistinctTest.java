package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Count;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateExpandWithinDistinctTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateExpandWithinDistinct.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static AggregateExpression countDistinct(Attribute x, boolean distinct) {
        return AggregateExpression$.MODULE$.apply(new Count(Catalyst.seq(List.<Expression>of(x))), Complete$.MODULE$,
                distinct, Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
    }

    private static LogicalPlan countDistinctPlan(LogicalPlan base, Attribute x) {
        return new Aggregate(Catalyst.seq(List.<Expression>of()),
                Catalyst.seq(List.<NamedExpression>of(alias(countDistinct(x, true), "cnt"))), base,
                Option.<AggregateHint>empty());
    }

    @Test
    void countDistinctBecomesDedupThenCount() {
        for (long seed = 1; seed <= 4; seed++) {
            var base = Support.relation("t", 1, List.of(), new Random(seed));
            var x = Catalyst.<Attribute>list(base.output()).get(0);
            var plan = countDistinctPlan(base, x);
            var rewritten = rule.apply(plan);
            assertNotEquals(plan, rewritten);

            var outer = assertInstanceOf(Aggregate.class, rewritten);
            assertTrue(Catalyst.list(outer.groupingExpressions()).isEmpty());
            var outerItems = Catalyst.list(outer.aggregateExpressions());
            assertEquals(1, outerItems.size());
            var outerCount = assertInstanceOf(AggregateExpression.class, ((Alias) outerItems.get(0)).child());
            assertFalse(outerCount.isDistinct());
            assertInstanceOf(Count.class, outerCount.aggregateFunction());

            var dedup = assertInstanceOf(Aggregate.class, outer.child());
            assertEquals(base, dedup.child());
            assertEquals(List.of(x), Catalyst.list(dedup.groupingExpressions()));
            assertTrue(Catalyst.list(dedup.aggregateExpressions()).size() == 1);
            assertEquals(List.of(x), Catalyst.list(dedup.aggregateExpressions()));
            assertEquals(Catalyst.list(dedup.output()).get(0),
                    Catalyst.<Expression>list(outerCount.aggregateFunction().children()).get(0));

            Assumptions.assumeTrue(Support.canExecute());
            assertEquals(Support.run(plan), Support.run(rewritten));
        }
    }

    @Test
    void nonDistinctCountIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Aggregate(Catalyst.seq(List.<Expression>of()),
                Catalyst.seq(List.<NamedExpression>of(alias(countDistinct(x, false), "cnt"))), base,
                Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void groupedCountDistinctIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var y = Catalyst.<Attribute>list(base.output()).get(1);
        var plan = new Aggregate(Catalyst.seq(List.<Expression>of(y)),
                Catalyst.seq(List.<NamedExpression>of(y, alias(countDistinct(x, true), "cnt"))), base,
                Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void wideChildCountDistinctIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = countDistinctPlan(base, x);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateAggregateFilterTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateAggregateFilter.json"));

    private static Aggregate aggregateOver(Attribute key, Attribute value, LogicalPlan child) {
        var max = AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var item = (NamedExpression) Alias$.MODULE$.apply((Expression) max, "m", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)), Catalyst.<NamedExpression>seq(List.of(key, item)), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void dropsTrueFilterUnderAggregate() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var columns = Catalyst.<Attribute>list(base.output());
        var key = columns.get(0);
        var value = columns.get(1);
        var plan = aggregateOver(key, value, new Filter(Literal$.MODULE$.apply(true), base));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate a && a.child() == base);
        assertEquals(Catalyst.list(plan.output()).size(), Catalyst.list(rewritten.output()).size());
        assertEquals(Catalyst.list(((Aggregate) plan).groupingExpressions()),
                Catalyst.list(((Aggregate) rewritten).groupingExpressions()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterWithColumnConditionAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(2));
        var columns = Catalyst.<Attribute>list(base.output());
        var key = columns.get(0);
        var value = columns.get(1);
        var plan = aggregateOver(key, value, new Filter(new GreaterThan(value, Literal$.MODULE$.apply(2)), base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAggregateWithoutFilterAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(3));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = aggregateOver(columns.get(0), columns.get(1), base);
        assertEquals(plan, rule.apply(plan));
    }
}

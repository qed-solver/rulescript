package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Distinct;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateAggDistinctForKeysTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateAggDistinctForKeys.json"));

    private static Aggregate aggregate(Attribute group, Attribute arg, boolean distinct, LogicalPlan child) {
        var function = new Max(arg);
        var agg = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, distinct,
                Option.<org.apache.spark.sql.catalyst.expressions.Expression>empty(), NamedExpression$.MODULE$.newExprId());
        NamedExpression item = Alias$.MODULE$.apply(agg, "m", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        return new Aggregate(Catalyst.seq(List.<org.apache.spark.sql.catalyst.expressions.Expression>of(group)),
                Catalyst.seq(List.of(group, item)), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void dropsDistinctOverUniqueGroupingKey() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var keyed = new Distinct(base);
        var x = Catalyst.<Attribute>list(keyed.output()).get(0);
        var plan = aggregate(x, x, true, keyed);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Aggregate.class, rewritten);
        var result = (Aggregate) rewritten;
        assertEquals(keyed, result.child());
        var aggs = Catalyst.<NamedExpression>list(result.aggregateExpressions());
        var agg = assertInstanceOf(AggregateExpression.class, ((org.apache.spark.sql.catalyst.expressions.Alias) aggs.get(1)).child());
        assertTrue(!agg.isDistinct());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsPlainAggregateUnchanged() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var keyed = new Distinct(base);
        var x = Catalyst.<Attribute>list(keyed.output()).get(0);
        var plan = aggregate(x, x, false, keyed);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsDistinctOnNonKeyColumnUnchanged() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(2));
        var keyed = new Distinct(base);
        var cols = Catalyst.<Attribute>list(keyed.output());
        var plan = aggregate(cols.get(0), cols.get(1), true, keyed);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsDistinctOverNullableKeyUnchanged() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var keyed = new Distinct(base);
        var x = Catalyst.<Attribute>list(keyed.output()).get(0);
        var plan = aggregate(x, x, true, keyed);
        assertEquals(plan, rule.apply(plan));
    }
}

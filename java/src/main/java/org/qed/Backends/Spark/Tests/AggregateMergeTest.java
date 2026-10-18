package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateMergeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateMerge.json"));

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression maxOf(Expression value) {
        return AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
    }

    private static Aggregate aggregate(List<Expression> groups, List<NamedExpression> items, org.apache.spark.sql.catalyst.plans.logical.LogicalPlan child) {
        return new Aggregate(Catalyst.seq(groups), Catalyst.seq(items), child, Option.<AggregateHint>empty());
    }

    @Test
    void mergesTwoKeyAggregateIntoItsFirstKey() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var second = new Add(x, Literal$.MODULE$.apply(1));
        var bottom = aggregate(List.<Expression>of(x, second),
                List.<NamedExpression>of(x, named(second, "k2"), named(maxOf(x), "v")), base);
        var topKey = Catalyst.<Attribute>list(bottom.output()).get(0);
        var plan = aggregate(List.<Expression>of(topKey), List.<NamedExpression>of(topKey), bottom);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate result && result.child() == base);
        var merged = (Aggregate) rewritten;
        assertEquals(List.of(x), Catalyst.list(merged.groupingExpressions()));
        assertEquals(List.of(x), Catalyst.list(merged.aggregateExpressions()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAggregateOverOneKeyAggregateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var bottom = aggregate(List.<Expression>of(x),
                List.<NamedExpression>of(x, named(maxOf(x), "v")), base);
        var topKey = Catalyst.<Attribute>list(bottom.output()).get(0);
        var plan = aggregate(List.<Expression>of(topKey), List.<NamedExpression>of(topKey), bottom);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAggregateGroupingOnSecondKeyAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var second = new Add(x, Literal$.MODULE$.apply(1));
        var bottom = aggregate(List.<Expression>of(x, second),
                List.<NamedExpression>of(x, named(second, "k2"), named(maxOf(x), "v")), base);
        var valueKey = Catalyst.<Attribute>list(bottom.output()).get(2);
        var plan = aggregate(List.<Expression>of(valueKey), List.<NamedExpression>of(valueKey), bottom);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAggregateOverTwoKeyAggregateWithoutCallAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var second = new Add(x, Literal$.MODULE$.apply(1));
        var bottom = aggregate(List.<Expression>of(x, second),
                List.<NamedExpression>of(x, named(second, "k2")), base);
        var topKey = Catalyst.<Attribute>list(bottom.output()).get(0);
        var plan = aggregate(List.<Expression>of(topKey), List.<NamedExpression>of(topKey), bottom);
        assertEquals(plan, rule.apply(plan));
    }
}

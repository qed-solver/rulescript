package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
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

class FilterWindowTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterWindowTranspose.json"));

    private static Aggregate maxByKey(LogicalPlan child, Attribute key, Attribute value) {
        var max = AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var maxItem = Alias$.MODULE$.apply((Expression) max, "m", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        return new Aggregate(Catalyst.seq(List.<Expression>of(key)),
                Catalyst.seq(List.<NamedExpression>of(key, maxItem)), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void pushesKeyFilterBelowAggregate() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var agg = maxByKey(base, cols.get(0), cols.get(1));
        var out = Catalyst.<Attribute>list(agg.output());
        var key = out.get(0);
        var maxValue = out.get(1);
        var keyCond = new GreaterThan(key, Literal$.MODULE$.apply(2));
        var maxCond = new GreaterThan(maxValue, Literal$.MODULE$.apply(1));
        var plan = new Filter(new And(keyCond, maxCond), agg);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter);
        var top = (Filter) rewritten;
        assertTrue(top.condition() instanceof GreaterThan);
        assertTrue(top.child() instanceof Aggregate);
        var newAgg = (Aggregate) top.child();
        assertTrue(newAgg.child() instanceof Filter);
        var pushed = (Filter) newAgg.child();
        assertTrue(pushed.condition() instanceof GreaterThan);
        assertTrue(pushed.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAFilterWithoutAConjunctionAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var agg = maxByKey(base, cols.get(0), cols.get(1));
        var maxValue = Catalyst.<Attribute>list(agg.output()).get(1);
        var plan = new Filter(new GreaterThan(maxValue, Literal$.MODULE$.apply(1)), agg);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAConjunctOnTheAggregatedValueAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var agg = maxByKey(base, cols.get(0), cols.get(1));
        var maxValue = Catalyst.<Attribute>list(agg.output()).get(1);
        var plan = new Filter(new And(new GreaterThan(maxValue, Literal$.MODULE$.apply(2)),
                new GreaterThan(maxValue, Literal$.MODULE$.apply(1))), agg);
        assertEquals(plan, rule.apply(plan));
    }
}

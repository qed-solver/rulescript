package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushSelectIntoGroupByTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushSelectIntoGroupBy.json"));

    private static NamedExpression maxItem(Attribute value) {
        var max = AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return Alias$.MODULE$.apply((Expression) max, "m", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Aggregate groupedBy(LogicalPlan child, Attribute key, NamedExpression item) {
        return new Aggregate(Catalyst.seq(List.<Expression>of(key)),
                Catalyst.seq(List.<NamedExpression>of(key, item)), child,
                Option.<AggregateHint>empty());
    }

    @Test
    void pushesGroupKeyFiltersBelowGroupBy() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var item = maxItem(y);
        var agg = groupedBy(base, x, item);
        var cond = new And(new GreaterThan(x, Literal$.MODULE$.apply(2)), new LessThan(x, Literal$.MODULE$.apply(15)));
        var plan = new Filter(cond, agg);
        var expected = groupedBy(new Filter(cond, base), x, item);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var ra = (Aggregate) rewritten;
        var ea = (Aggregate) expected;
        assertEquals(ea.groupingExpressions(), ra.groupingExpressions());
        assertEquals(ea.child(), ra.child());
        assertEquals(ea.output(), ra.output());
        var rewrittenItem = (Alias) Catalyst.<NamedExpression>list(ra.aggregateExpressions()).get(1);
        var expectedItem = (Alias) Catalyst.<NamedExpression>list(ea.aggregateExpressions()).get(1);
        assertEquals(expectedItem.exprId(), rewrittenItem.exprId());
        assertEquals(((AggregateExpression) expectedItem.child()).aggregateFunction(),
                ((AggregateExpression) rewrittenItem.child()).aggregateFunction());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOnAggregateOutputAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(2));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var item = maxItem(y);
        var agg = groupedBy(base, x, item);
        var m = Catalyst.<Attribute>list(agg.output()).get(1);
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(2)),
                new GreaterThan(m, Literal$.MODULE$.apply(1))), agg);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterOverNullableInputAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var agg = groupedBy(base, x, maxItem(y));
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), agg);
        assertEquals(plan, rule.apply(plan));
    }
}

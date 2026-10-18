package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Subtract;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.aggregate.Min;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushAggDistinctIntoGroupByTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PushAggDistinctIntoGroupBy.json"));

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static AggregateExpression aggregate(org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction function, boolean distinct) {
        return AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, distinct,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
    }

    private static Aggregate groupBy(List<Expression> keys, List<NamedExpression> items, LogicalPlan child) {
        return new Aggregate(Catalyst.<Expression>seq(keys), Catalyst.<NamedExpression>seq(items), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static LogicalPlan unwrapProject(LogicalPlan plan) {
        return plan instanceof Project p ? p.child() : plan;
    }

    @Test
    void pushesDistinctMaxBelowAGroupByOverTwoKeys() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        Expression k1 = a;
        Expression k2 = new Add(a, Literal$.MODULE$.apply(1));
        Expression x = new Subtract(a, Literal$.MODULE$.apply(1));
        var distinctMax = aggregate(new Max(x), true);
        var plan = groupBy(List.of(k1, k2),
                List.of(named(k1, "k1"), named(k2, "k2"), named(distinctMax, "f")), base);

        var rewritten = unwrapProject(rule.apply(plan));

        assertNotEquals(plan, rule.apply(plan));
        assertTrue(rewritten instanceof Aggregate, "top node should be an Aggregate: " + rewritten);
        var outer = (Aggregate) rewritten;
        assertTrue(outer.child() instanceof Aggregate, "outer aggregate should sit on an inner aggregate: " + outer);
        var inner = (Aggregate) outer.child();
        assertEquals(3, inner.groupingExpressions().size());
        assertEquals(2, outer.groupingExpressions().size());
        var outerAggs = scala.jdk.javaapi.CollectionConverters.asJava(outer.aggregateExpressions());
        assertTrue(outerAggs.stream().anyMatch(e -> e instanceof Alias al && al.child() instanceof AggregateExpression ae
                && !ae.isDistinct() && ae.aggregateFunction() instanceof Max));
        Assumptions.assumeTrue(Support.canExecute());
        for (int seed = 1; seed <= 5; seed++) {
            var data = Support.relation("t", 1, List.of(), new Random(seed));
            var dataPlan = distinctMaxPlan(data);
            assertEquals(Support.run(dataPlan), Support.run(rule.apply(dataPlan)));
        }
    }

    private static LogicalPlan distinctMaxPlan(LogicalPlan base) {
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        Expression k1 = a;
        Expression k2 = new Add(a, Literal$.MODULE$.apply(1));
        Expression x = new Subtract(a, Literal$.MODULE$.apply(1));
        var distinctMax = aggregate(new Max(x), true);
        return groupBy(List.of(k1, k2),
                List.of(named(k1, "k1"), named(k2, "k2"), named(distinctMax, "f")), base);
    }

    @Test
    void leavesANonDistinctAggregateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        Expression k1 = a;
        Expression k2 = new Add(a, Literal$.MODULE$.apply(1));
        Expression x = new Subtract(a, Literal$.MODULE$.apply(1));
        var plainMax = aggregate(new Max(x), false);
        var plan = groupBy(List.of(k1, k2),
                List.of(named(k1, "k1"), named(k2, "k2"), named(plainMax, "f")), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesADistinctAggregateWithAnExtraNonDistinctAggregateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        Expression k1 = a;
        Expression k2 = new Add(a, Literal$.MODULE$.apply(1));
        Expression x = new Subtract(a, Literal$.MODULE$.apply(1));
        var distinctMax = aggregate(new Max(x), true);
        var extraMin = aggregate(new Min(a), false);
        var plan = groupBy(List.of(k1, k2),
                List.of(named(k1, "k1"), named(k2, "k2"), named(distinctMax, "f"), named(extraMin, "g")), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesADistinctAggregateWithThreeGroupingKeysAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        Expression k1 = a;
        Expression k2 = new Add(a, Literal$.MODULE$.apply(1));
        Expression k3 = new Subtract(a, Literal$.MODULE$.apply(2));
        Expression x = new Subtract(a, Literal$.MODULE$.apply(1));
        var distinctMax = aggregate(new Max(x), true);
        var plan = groupBy(List.of(k1, k2, k3),
                List.of(named(k1, "k1"), named(k2, "k2"), named(k3, "k3"), named(distinctMax, "f")), base);
        assertEquals(plan, rule.apply(plan));
    }
}

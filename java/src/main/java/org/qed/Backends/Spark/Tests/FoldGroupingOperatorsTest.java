package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldGroupingOperatorsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldGroupingOperators.json"));

    private static Aggregate innerAggregate(LogicalPlan base, Attribute key, Attribute value) {
        var max = AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var alias = Alias$.MODULE$.apply((Expression) max, "v", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)),
                Catalyst.<NamedExpression>seq(List.of(key, alias)), base,
                Option.<AggregateHint>empty());
    }

    private static Aggregate outerAggregate(LogicalPlan child, List<Expression> groups, List<NamedExpression> items) {
        return new Aggregate(Catalyst.<Expression>seq(groups), Catalyst.<NamedExpression>seq(items), child,
                Option.<AggregateHint>empty());
    }

    @Test
    void foldsGroupingOverGroupingWhenOuterKeepsOnlyTheKey() {
        var base = Support.relation("s", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = innerAggregate(base, x, x);
        var innerKey = Catalyst.<Attribute>list(inner.output()).get(0);
        var plan = outerAggregate(inner, List.<Expression>of(innerKey), List.<NamedExpression>of(innerKey));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate a && a.child() == base);
        assertEquals(List.of(x), Catalyst.list(((Aggregate) rewritten).groupingExpressions()));
        assertEquals(List.of(x), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesGroupingOverAGroupingColumnAlone() {
        var base = Support.relation("s", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = innerAggregate(base, x, x);
        var innerValue = Catalyst.<Attribute>list(inner.output()).get(1);
        var plan = outerAggregate(inner, List.<Expression>of(innerValue), List.<NamedExpression>of(innerValue));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesGroupingThatKeepsAnAggregateCallAlone() {
        var base = Support.relation("s", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = innerAggregate(base, x, x);
        var innerKey = Catalyst.<Attribute>list(inner.output()).get(0);
        var innerValue = Catalyst.<Attribute>list(inner.output()).get(1);
        var max = AggregateExpression$.MODULE$.apply(new Max(innerValue), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var alias = Alias$.MODULE$.apply((Expression) max, "m", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        var plan = outerAggregate(inner, List.<Expression>of(innerKey),
                List.<NamedExpression>of(innerKey, alias));
        assertEquals(plan, rule.apply(plan));
    }
}

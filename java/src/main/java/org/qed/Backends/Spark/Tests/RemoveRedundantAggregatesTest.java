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

class RemoveRedundantAggregatesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RemoveRedundantAggregates.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression maxOf(Expression child) {
        return AggregateExpression$.MODULE$.apply(new Max(child), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
    }

    private static Aggregate aggregate(List<Expression> groups, List<NamedExpression> items, LogicalPlan child) {
        return new Aggregate(Catalyst.seq(groups), Catalyst.seq(items), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void collapsesUnusedLowerAggregateToDistinctKeys() {
        var base = Support.relation("S", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var k2 = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var lower = aggregate(List.<Expression>of(x, k2),
                List.<NamedExpression>of(x, alias(k2, "g2"), alias(maxOf(x), "v")), base);
        var key = Catalyst.<Attribute>list(lower.output()).get(0);
        var upper = aggregate(List.<Expression>of(key), List.<NamedExpression>of(key), lower);

        var rewritten = rule.apply(upper);
        assertNotEquals(upper, rewritten);
        assertTrue(rewritten instanceof Aggregate top && top.child() instanceof Project p && p.child() == base
                && Catalyst.list(p.projectList()).size() == 1 && Catalyst.list(top.aggregateExpressions()).size() == 1);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(upper), Support.run(rewritten));
    }

    @Test
    void keepsLowerAggregateWhenUpperGroupsBySecondKey() {
        var base = Support.relation("S", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var k2 = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var lower = aggregate(List.<Expression>of(x, k2),
                List.<NamedExpression>of(x, alias(k2, "g2"), alias(maxOf(x), "v")), base);
        var secondKey = Catalyst.<Attribute>list(lower.output()).get(1);
        var upper = aggregate(List.<Expression>of(secondKey), List.<NamedExpression>of(secondKey), lower);
        assertEquals(upper, rule.apply(upper));
    }

    @Test
    void keepsLowerAggregateWhenUpperHasAnAggregateCall() {
        var base = Support.relation("S", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var k2 = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var lower = aggregate(List.<Expression>of(x, k2),
                List.<NamedExpression>of(x, alias(k2, "g2"), alias(maxOf(x), "v")), base);
        var key = Catalyst.<Attribute>list(lower.output()).get(0);
        var upper = aggregate(List.<Expression>of(key),
                List.<NamedExpression>of(key, alias(maxOf(key), "m")), lower);
        assertEquals(upper, rule.apply(upper));
    }

    @Test
    void keepsLowerAggregateWhenInputHasTwoColumns() {
        var base = Support.relation("S", 2, List.of(), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var lower = aggregate(List.<Expression>of(x, y),
                List.<NamedExpression>of(x, alias(y, "g2"), alias(maxOf(x), "v")), base);
        var key = Catalyst.<Attribute>list(lower.output()).get(0);
        var upper = aggregate(List.<Expression>of(key), List.<NamedExpression>of(key), lower);
        assertEquals(upper, rule.apply(upper));
    }
}

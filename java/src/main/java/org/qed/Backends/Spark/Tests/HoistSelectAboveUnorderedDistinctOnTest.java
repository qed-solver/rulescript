package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HoistSelectAboveUnorderedDistinctOnTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("HoistSelectAboveUnorderedDistinctOn.json"));

    private static Aggregate groupBy(List<Attribute> keys, List<Attribute> outputs, LogicalPlan child) {
        return new Aggregate(Catalyst.<Expression>seq(keys), Catalyst.<NamedExpression>seq(outputs), child,
                scala.Option.empty());
    }

    @Test
    void hoistsGroupConstantFilterAboveDistinct() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var k1 = attrs.get(0);
        var k2 = attrs.get(1);
        var predicate = new GreaterThan(k1, Literal$.MODULE$.apply(1));
        var plan = groupBy(List.of(k1, k2), List.of(k1, k2), new Filter(predicate, base));
        var expected = new Filter(predicate, groupBy(List.of(k1, k2), List.of(k1, k2), base));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterAboveDistinctAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(2));
        var attrs = Catalyst.<Attribute>list(base.output());
        var k1 = attrs.get(0);
        var k2 = attrs.get(1);
        var plan = new Filter(new GreaterThan(k1, Literal$.MODULE$.apply(1)),
                groupBy(List.of(k1, k2), List.of(k1, k2), base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesGroupingOnFewerKeysAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var k1 = attrs.get(0);
        var plan = groupBy(List.of(k1), List.of(k1), new Filter(new GreaterThan(k1, Literal$.MODULE$.apply(1)), base));
        assertEquals(plan, rule.apply(plan));
    }
}

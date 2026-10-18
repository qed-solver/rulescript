package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AggregateUnionAggregateTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateUnionAggregate.json"));

    private static Aggregate dedup(LogicalPlan child) {
        var attr = Catalyst.<Attribute>list(child.output()).get(0);
        return new Aggregate(Catalyst.<Expression>seq(List.of(attr)), Catalyst.<NamedExpression>seq(List.of(attr)),
                child, Option.<AggregateHint>empty());
    }

    @Test
    void mergesDistinctSecondInputIntoUnionAll() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var l = Support.relation("L", 1, List.of(), new Random(2));
        var plan = dedup(new Union(Catalyst.<LogicalPlan>seq(List.of(a, dedup(l))), false, false));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(dedup(new Union(Catalyst.<LogicalPlan>seq(List.of(a, l)), false, false)), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesUnionWithFilteredSecondInputAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var l = Support.relation("L", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(l.output()).get(0);
        var filtered = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), l);
        var plan = dedup(new Union(Catalyst.<LogicalPlan>seq(List.of(a, filtered)), false, false));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIntersectInsteadOfUnionAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var l = Support.relation("L", 1, List.of(), new Random(2));
        var plan = dedup(new Intersect(a, dedup(l), false));
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateUnionTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateUnionTranspose.json"));

    private static Aggregate dedup(LogicalPlan child) {
        var columns = Catalyst.<Attribute>list(child.output());
        return new Aggregate(Catalyst.<Expression>seq(columns), Catalyst.<NamedExpression>seq(columns), child,
                Option.<AggregateHint>empty());
    }

    @Test
    void dedupOfUnionAllBecomesDedupOfDedupedInputs() {
        var a = Support.relation("A", 2, List.of(), new Random(1));
        var b = Support.relation("B", 2, List.of(), new Random(2));
        var plan = dedup(new Union(Catalyst.<LogicalPlan>seq(List.of(a, b)), false, false));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var top = (Aggregate) rewritten;
        assertTrue(top.child() instanceof Union);
        var inputs = Catalyst.<LogicalPlan>list(top.child().children());
        assertEquals(2, inputs.size());
        assertTrue(inputs.get(0) instanceof Aggregate);
        assertTrue(inputs.get(1) instanceof Aggregate);
        assertEquals(a, ((Aggregate) inputs.get(0)).child());
        assertEquals(b, ((Aggregate) inputs.get(1)).child());
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void dedupOfThreeWayUnionIsLeftAlone() {
        var a = Support.relation("A", 2, List.of(), new Random(1));
        var b = Support.relation("B", 2, List.of(), new Random(2));
        var c = Support.relation("C", 2, List.of(), new Random(3));
        var plan = dedup(new Union(Catalyst.<LogicalPlan>seq(List.of(a, b, c)), false, false));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void groupingOnOneColumnOfUnionIsLeftAlone() {
        var a = Support.relation("A", 2, List.of(), new Random(1));
        var b = Support.relation("B", 2, List.of(), new Random(2));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(a, b)), false, false);
        var first = Catalyst.<Attribute>list(union.output()).get(0);
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(first)), Catalyst.<NamedExpression>seq(List.of(first)),
                union, Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }
}

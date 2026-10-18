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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombineUnionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CombineUnions.json"));

    private static LogicalPlan distinct(LogicalPlan child) {
        var output = Catalyst.<Attribute>list(child.output());
        return new Aggregate(Catalyst.seq(new ArrayList<Expression>(output)),
                Catalyst.seq(new ArrayList<NamedExpression>(output)), child,
                Option.<AggregateHint>empty());
    }

    private static LogicalPlan unionAll(LogicalPlan... inputs) {
        return new Union(Catalyst.seq(List.of(inputs)), false, false);
    }

    @Test
    void flattensNestedUnionAndDropsInnerDistinct() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var plan = distinct(unionAll(a, distinct(unionAll(b, c)), d));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var union = (Union) ((Aggregate) rewritten).child();
        assertEquals(List.of(a, b, c, d), Catalyst.list(union.children()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesUnionWithoutOuterDistinctAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var plan = unionAll(a, distinct(unionAll(b, c)), d);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesMiddleUnionWithoutDistinctAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var plan = distinct(unionAll(a, unionAll(b, c), d));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesMiddleInputThatIsNotAUnionAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var plan = distinct(unionAll(a, distinct(b), d));
        assertEquals(plan, rule.apply(plan));
    }
}

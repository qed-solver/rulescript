package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushSelectIntoUnorderedDistinctOnTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("PushSelectIntoUnorderedDistinctOn.json"));

    @Test
    void pushesBothConjunctsBelowIdentityDistinct() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var distinct = new Aggregate(Catalyst.<Expression>seq(List.of(a, b)), Catalyst.<org.apache.spark.sql.catalyst.expressions.NamedExpression>seq(List.of(a, b)),
                base, Option.empty());
        var plan = new Filter(new And(new GreaterThan(a, Literal$.MODULE$.apply(1)), new LessThan(b, Literal$.MODULE$.apply(3))), distinct);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var top = assertInstanceOf(Aggregate.class, rewritten);
        var below = assertInstanceOf(Filter.class, top.child());
        assertTrue(below.child() == base);
        assertInstanceOf(And.class, below.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesDistinctOnFewerColumnsAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(2));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var distinct = new Aggregate(Catalyst.<Expression>seq(List.of(a)), Catalyst.<org.apache.spark.sql.catalyst.expressions.NamedExpression>seq(List.of(a)),
                base, Option.empty());
        var plan = new Filter(new And(new GreaterThan(a, Literal$.MODULE$.apply(1)), new LessThan(b, Literal$.MODULE$.apply(3))), distinct);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterWithoutDistinctAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var plan = new Filter(new And(new GreaterThan(a, Literal$.MODULE$.apply(1)), new LessThan(b, Literal$.MODULE$.apply(3))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

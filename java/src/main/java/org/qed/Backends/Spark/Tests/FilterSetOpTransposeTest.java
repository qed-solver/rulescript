package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Distinct;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterSetOpTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterSetOpTranspose.json"));

    @Test
    void pushesFilterIntoBothInputsOfUnionAll() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var union = new Union(Catalyst.seq(List.of(left, right)), false, false);
        var unionX = Catalyst.<Attribute>list(union.output()).get(0);
        var plan = new Filter(new GreaterThan(unionX, Literal$.MODULE$.apply(2)), union);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Union);
        var parts = Catalyst.<LogicalPlan>list(((Union) rewritten).children());
        assertEquals(2, parts.size());
        var leftPart = (Filter) parts.get(0);
        var rightPart = (Filter) parts.get(1);
        assertSame(left, leftPart.child());
        assertSame(right, rightPart.child());
        var leftX = Catalyst.<Attribute>list(left.output()).get(0);
        var rightX = Catalyst.<Attribute>list(right.output()).get(0);
        assertEquals(new GreaterThan(leftX, Literal$.MODULE$.apply(2)), leftPart.condition());
        assertEquals(new GreaterThan(rightX, Literal$.MODULE$.apply(2)), rightPart.condition());
        assertEquals(unionX.exprId(), Catalyst.<Attribute>list(rewritten.output()).get(0).exprId());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOverIntersectAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var intersect = new Intersect(left, right, false);
        var x = Catalyst.<Attribute>list(intersect.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), intersect);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterOverThreeWayUnionAlone() {
        var first = Support.relation("l", 1, List.of(), new Random(1));
        var second = Support.relation("r", 1, List.of(), new Random(2));
        var third = Support.relation("m", 1, List.of(), new Random(3));
        var union = new Union(Catalyst.seq(List.of(first, second, third)), false, false);
        var x = Catalyst.<Attribute>list(union.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), union);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterOverDistinctUnionAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var union = new Union(Catalyst.seq(List.of(left, right)), false, false);
        var distinct = new Distinct(union);
        var x = Catalyst.<Attribute>list(distinct.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), distinct);
        assertEquals(plan, rule.apply(plan));
    }
}

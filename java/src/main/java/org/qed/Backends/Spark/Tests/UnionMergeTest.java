package org.qed.spark;

import org.apache.spark.sql.catalyst.plans.logical.Distinct;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnionMergeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("UnionMerge.json"));

    @Test
    void mergesNestedUnionAllIntoOneUnion() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var inner = new Union(Catalyst.<LogicalPlan>seq(List.of(b, c)), false, false);
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(a, inner, d)), false, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Union);
        assertEquals(List.of(a, b, c, d), Catalyst.<LogicalPlan>list(((Union) rewritten).children()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFlatUnionAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(a, b, c, d)), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNestedUnionInFirstPositionAlone() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var inner = new Union(Catalyst.<LogicalPlan>seq(List.of(a, b)), false, false);
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(inner, c, d)), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void doesNotMatchDistinctUnion() {
        var a = Support.relation("a", 1, List.of(), new Random(1));
        var b = Support.relation("b", 1, List.of(), new Random(2));
        var c = Support.relation("c", 1, List.of(), new Random(3));
        var d = Support.relation("d", 1, List.of(), new Random(4));
        var inner = new Union(Catalyst.<LogicalPlan>seq(List.of(b, c)), false, false);
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(a, inner, d)), false, false);
        var plan = new Distinct(union);
        assertFalse(rule.matches(plan));
    }
}

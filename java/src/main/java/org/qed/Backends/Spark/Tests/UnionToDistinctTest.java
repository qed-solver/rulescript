package org.qed.spark;

import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Distinct;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnionToDistinctTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Path.of(Support.generated().toString(), "UnionToDistinct.json"));

    private static LogicalPlan unwrapProject(LogicalPlan plan) {
        return plan instanceof Project p ? p.child() : plan;
    }

    @Test
    void distinctUnionOfTwoOneColumnInputsBecomesGroupedUnionAll() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var union = new Union(Catalyst.<LogicalPlan>seq(List.of(a, b)), false, false);
        var plan = new Distinct(union);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var top = unwrapProject(rewritten);
        assertTrue(top instanceof Aggregate);
        var aggregate = (Aggregate) top;
        assertEquals(1, Catalyst.list(aggregate.groupingExpressions()).size());
        assertTrue(aggregate.child() instanceof Union);
        assertEquals(2, Catalyst.list(((Union) aggregate.child()).children()).size());
        assertEquals(1, Catalyst.list(rewritten.output()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void unionAllOfTwoInputsIsLeftAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var plan = new Union(Catalyst.<LogicalPlan>seq(List.of(a, b)), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void distinctUnionOfThreeInputsIsLeftAlone() {
        var a = Support.relation("A", 1, List.of(), new Random(1));
        var b = Support.relation("B", 1, List.of(), new Random(2));
        var c = Support.relation("C", 1, List.of(), new Random(3));
        var plan = new Distinct(new Union(Catalyst.<LogicalPlan>seq(List.of(a, b, c)), false, false));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void distinctUnionOfTwoTwoColumnInputsIsLeftAlone() {
        var a = Support.relation("A", 2, List.of(), new Random(1));
        var b = Support.relation("B", 2, List.of(), new Random(2));
        var plan = new Distinct(new Union(Catalyst.<LogicalPlan>seq(List.of(a, b)), false, false));
        assertEquals(plan, rule.apply(plan));
    }
}

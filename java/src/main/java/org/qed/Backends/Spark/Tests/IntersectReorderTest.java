package org.qed.spark;

import org.apache.spark.sql.catalyst.plans.logical.Except;
import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class IntersectReorderTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("IntersectReorder.json"));

    @Test
    void reordersThreeWayDistinctIntersect() {
        var a = Support.relation("a", 1, List.of(0), new Random(1));
        var b = Support.relation("b", 1, List.of(0), new Random(2));
        var c = Support.relation("c", 1, List.of(0), new Random(3));
        var plan = new Intersect(new Intersect(a, b, false), c, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var project = assertInstanceOf(Project.class, rewritten);
        assertEquals(new Intersect(new Intersect(c, a, false), b, false), project.child());
        assertEquals(plan.output(), rewritten.output());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAllIntersectAlone() {
        var a = Support.relation("a", 1, List.of(0), new Random(1));
        var b = Support.relation("b", 1, List.of(0), new Random(2));
        var c = Support.relation("c", 1, List.of(0), new Random(3));
        var plan = new Intersect(new Intersect(a, b, true), c, true);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIntersectOfExceptAlone() {
        var a = Support.relation("a", 1, List.of(0), new Random(1));
        var b = Support.relation("b", 1, List.of(0), new Random(2));
        var c = Support.relation("c", 1, List.of(0), new Random(3));
        var plan = new Intersect(new Except(a, b, false), c, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoWayIntersectAlone() {
        var a = Support.relation("a", 1, List.of(0), new Random(1));
        var b = Support.relation("b", 1, List.of(0), new Random(2));
        var plan = new Intersect(a, b, false);
        assertEquals(plan, rule.apply(plan));
    }
}

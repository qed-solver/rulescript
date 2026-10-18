package org.qed.spark;

import org.apache.spark.sql.catalyst.plans.logical.Intersect;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnionEliminatorTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("UnionEliminator.json"));

    @Test
    void dropsASingleChildUnionAll() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Union(Catalyst.seq(List.of(base)), false, false);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation);
        assertEquals(Catalyst.list(base.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void dropsASingleChildUnionAllOverAnotherInput() {
        var base = Support.relation("n", 1, List.of(), new Random(7));
        var plan = new Union(Catalyst.seq(List.of(base)), false, false);
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof LocalRelation);
        assertEquals(Catalyst.list(base.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesATwoChildUnionAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(2));
        var right = Support.relation("r", 1, List.of(), new Random(3));
        var plan = new Union(Catalyst.seq(List.of(left, right)), false, false);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnIntersectAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(4));
        var right = Support.relation("r", 1, List.of(), new Random(5));
        var plan = new Intersect(left, right, true);
        assertEquals(plan, rule.apply(plan));
    }
}

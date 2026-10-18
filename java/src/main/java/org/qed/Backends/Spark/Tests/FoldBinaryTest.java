package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldBinaryTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldBinary.json"));

    @Test
    void foldsTrueAndFalseFilterToFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new And(Literal$.MODULE$.apply(true), Literal$.MODULE$.apply(false)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(Literal$.MODULE$.apply(false), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesReversedAndOfTrueAndFalseAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new And(Literal$.MODULE$.apply(false), Literal$.MODULE$.apply(true)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOrOfTrueAndFalseAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Or(Literal$.MODULE$.apply(true), Literal$.MODULE$.apply(false)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

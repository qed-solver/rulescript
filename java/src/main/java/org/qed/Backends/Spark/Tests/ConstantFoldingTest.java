package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstantFoldingTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ConstantFolding.json"));

    @Test
    void foldsTrueAndFalseConditionToFalse() {
        LocalRelation base = Support.relation("Source", 1, List.of(), new Random(1));
        var cond = new And(Literal$.MODULE$.TrueLiteral(), Literal$.MODULE$.FalseLiteral());
        var plan = new Filter(cond, base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f
                && f.child().equals(base)
                && f.condition().equals(Literal$.MODULE$.FalseLiteral()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOrOfTrueAndFalseAlone() {
        LocalRelation base = Support.relation("Source", 1, List.of(), new Random(1));
        var cond = new Or(Literal$.MODULE$.TrueLiteral(), Literal$.MODULE$.FalseLiteral());
        var plan = new Filter(cond, base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAndOfTrueAndTrueAlone() {
        LocalRelation base = Support.relation("Source", 1, List.of(), new Random(1));
        var cond = new And(Literal$.MODULE$.TrueLiteral(), Literal$.MODULE$.TrueLiteral());
        var plan = new Filter(cond, base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAndOfFalseAndTrueAlone() {
        LocalRelation base = Support.relation("Source", 1, List.of(), new Random(1));
        var cond = new And(Literal$.MODULE$.FalseLiteral(), Literal$.MODULE$.TrueLiteral());
        var plan = new Filter(cond, base);
        assertEquals(plan, rule.apply(plan));
    }
}

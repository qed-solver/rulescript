package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldUnaryTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldUnary.json"));

    @Test
    void foldsNotTrueFilterToFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(Literal$.MODULE$.TrueLiteral()), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && f.condition().equals(Literal$.MODULE$.FalseLiteral()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNotFalseFilterAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(Literal$.MODULE$.FalseLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotOfNonConstantAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        Attribute x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(new org.apache.spark.sql.catalyst.expressions.GreaterThan(
                x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

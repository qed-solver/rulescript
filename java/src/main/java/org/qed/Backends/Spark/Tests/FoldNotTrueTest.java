package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNotTrueTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNotTrue.json"));

    @Test
    void foldsNotTrueToFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(Literal$.MODULE$.TrueLiteral()), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(Literal$.MODULE$.FalseLiteral(), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void foldsNotTrueOverNullableInputToFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(Literal$.MODULE$.TrueLiteral()), new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), base));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.condition().equals(Literal$.MODULE$.FalseLiteral()));
    }

    @Test
    void leavesNotFalseAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(Literal$.MODULE$.FalseLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesPlainTrueAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(Literal$.MODULE$.TrueLiteral(), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotOfComparisonAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

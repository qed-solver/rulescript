package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldEqFalseTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldEqFalse.json"));

    @Test
    void rewritesEqualsFalseIntoNegation() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new EqualTo(left, Literal$.MODULE$.apply(false)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition() instanceof Not);
        assertEquals(left, ((Not) ((Filter) rewritten).condition()).child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void rewritesEqualsFalseOnNullableColumn() {
        var base = Support.relation("t", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new EqualTo(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new EqualTo(left, Literal$.MODULE$.apply(false)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition() instanceof Not);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesEqualsTrueAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.apply(true)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullSafeEqualsFalseAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualNullSafe(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.apply(false)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

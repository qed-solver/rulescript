package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldEqTrueTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldEqTrue.json"));

    @Test
    void foldsEqualityWithTrueIntoTheCondition() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var pred = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new EqualTo(pred, Literal$.MODULE$.apply(true)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && pred.equals(f.condition()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesEqualityWithFalseAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.apply(false)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTrueOnTheLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(Literal$.MODULE$.apply(true), new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

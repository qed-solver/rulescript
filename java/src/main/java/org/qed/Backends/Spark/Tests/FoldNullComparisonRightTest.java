package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.IntegerType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNullComparisonRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNullComparisonRight.json"));

    @Test
    void foldsEqualityAgainstBooleanNullToNull() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new EqualTo(left, new Literal(null, BooleanType$.MODULE$)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        var condition = ((Filter) rewritten).condition();
        assertTrue(condition instanceof Literal l && l.value() == null && l.dataType() == BooleanType$.MODULE$);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void foldsEqualityOfNonNullColumnAgainstNullToNull() {
        var base = Support.relation("t", 1, List.of(0), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var left = new EqualTo(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new EqualTo(left, new Literal(null, BooleanType$.MODULE$)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNullSafeEqualityAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualNullSafe(new GreaterThan(x, Literal$.MODULE$.apply(2)),
                new Literal(null, BooleanType$.MODULE$)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullOnTheLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(new Literal(null, BooleanType$.MODULE$),
                new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesEqualityAgainstNonNullLiteralAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.TrueLiteral()),
                base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesEqualityAgainstNullOfAnotherTypeAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(x, new Literal(null, IntegerType$.MODULE$)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

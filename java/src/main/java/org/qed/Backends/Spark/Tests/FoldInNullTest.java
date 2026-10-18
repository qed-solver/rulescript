package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
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

class FoldInNullTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldInNull.json"));

    @Test
    void equalityWithNullBecomesNullFilter() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(x, new Literal(null, IntegerType$.MODULE$)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(new Literal(null, BooleanType$.MODULE$), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void equalityWithNonNullLiteralIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nullOnTheLeftOfEqualityIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(new Literal(null, IntegerType$.MODULE$), x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void notEqualToNullIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(new EqualTo(x, new Literal(null, IntegerType$.MODULE$))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nullSafeEqualityWithNullIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualNullSafe(x, new Literal(null, IntegerType$.MODULE$)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

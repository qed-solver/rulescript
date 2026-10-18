package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNullBinaryLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNullBinaryLeft.json"));

    @Test
    void foldsNullLeftLessThanInFilterToNullBoolean() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new LessThan(new Literal(null, IntegerType$.MODULE$), x), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter);
        var filter = (Filter) rewritten;
        assertTrue(filter.child() == base);
        assertTrue(filter.condition() instanceof Literal);
        var cond = (Literal) filter.condition();
        assertNull(cond.value());
        assertEquals(BooleanType$.MODULE$, cond.dataType());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNullOnRightOfLessThanAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new LessThan(x, new Literal(null, IntegerType$.MODULE$)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonNullLeftOfLessThanAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new LessThan(Literal$.MODULE$.apply(2), x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullLeftOfGreaterThanAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(new Literal(null, IntegerType$.MODULE$), x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoColumnInputAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(5));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new LessThan(new Literal(null, IntegerType$.MODULE$), x), base);
        assertEquals(plan, rule.apply(plan));
    }
}

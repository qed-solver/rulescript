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
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNullBinaryRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNullBinaryRight.json"));

    @Test
    void foldsLessThanAgainstNullIntoNullFilter() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new LessThan(x, new Literal(null, IntegerType$.MODULE$)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && f.condition() instanceof Literal l && l.value() == null && l.dataType() == BooleanType$.MODULE$);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesGreaterThanAgainstNullAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, new Literal(null, IntegerType$.MODULE$)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullOnTheLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new LessThan(new Literal(null, IntegerType$.MODULE$), x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonNullRightOperandAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new LessThan(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
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

class FoldNotNullTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNotNull.json"));

    @Test
    void replacesNotNullWithNullLiteral() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var nullBool = new Literal(null, BooleanType$.MODULE$);
        var plan = new Filter(new Not(nullBool), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && f.condition() instanceof Literal l && l.value() == null
                && l.dataType().equals(BooleanType$.MODULE$));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterWithNullLiteralAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Literal(null, BooleanType$.MODULE$), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotOfNonNullLiteralAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(Literal$.MODULE$.apply(true)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotOfNullIntegerAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(new Literal(null, IntegerType$.MODULE$)), base);
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

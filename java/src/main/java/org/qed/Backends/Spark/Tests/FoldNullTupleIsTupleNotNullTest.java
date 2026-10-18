package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.types.IntegerType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNullTupleIsTupleNotNullTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNullTupleIsTupleNotNull.json"));

    @Test
    void foldsNotNullOfNullElementToFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var nullElement = new Literal(null, IntegerType$.MODULE$);
        var plan = new Filter(new And(new IsNotNull(nullElement), new IsNotNull(x)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(Literal$.MODULE$.FalseLiteral(), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void foldsWhenOtherElementIsNonNullColumn() {
        var base = Support.relation("t", 1, List.of(0), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var nullElement = new Literal(null, IntegerType$.MODULE$);
        var plan = new Filter(new And(new IsNotNull(nullElement), new IsNotNull(x)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(Literal$.MODULE$.FalseLiteral(), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesIsNullInsteadOfIsNotNullAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var nullElement = new Literal(null, IntegerType$.MODULE$);
        var plan = new Filter(new And(new IsNull(nullElement), new IsNotNull(x)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonNullLiteralElementAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var twoElement = new Literal(2, IntegerType$.MODULE$);
        var plan = new Filter(new And(new IsNotNull(twoElement), new IsNotNull(x)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesSingleConjunctAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(5));
        var nullElement = new Literal(null, IntegerType$.MODULE$);
        var plan = new Filter(new IsNotNull(nullElement), base);
        assertEquals(plan, rule.apply(plan));
    }
}

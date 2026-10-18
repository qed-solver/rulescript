package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.IntegerType$;
import org.apache.spark.sql.types.NullType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldIsNullTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldIsNull.json"));

    private static org.apache.spark.sql.catalyst.expressions.Literal nullInt() {
        return Literal$.MODULE$.create(null, IntegerType$.MODULE$);
    }

    private static org.apache.spark.sql.catalyst.expressions.Literal trueLiteral() {
        return Literal$.MODULE$.create(Boolean.TRUE, BooleanType$.MODULE$);
    }

    @Test
    void foldsIsNullOfNullIntegerToTrue() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new IsNull(nullInt()), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(trueLiteral(), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void foldsOverANonNullableOneColumnSource() {
        var base = Support.relation("t", 1, List.of(0), new Random(7));
        var plan = new Filter(new IsNull(nullInt()), base);
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(trueLiteral(), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesIsNullOfNonNullLiteralAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new IsNull(Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNullOfColumnAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        Attribute x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new IsNull(x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNotNullOfNullLiteralAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(new IsNull(nullInt())), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNullOfUntypedNullAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var untyped = Literal$.MODULE$.create(null, NullType$.MODULE$);
        var plan = new Filter(new IsNull(untyped), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNullInsideAConjunctionAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        Attribute x = Catalyst.<Attribute>list(base.output()).get(0);
        var condition = new And(new IsNull(nullInt()), new GreaterThan(x, Literal$.MODULE$.apply(2)));
        var plan = new Filter(condition, base);
        assertEquals(plan, rule.apply(plan));
    }
}

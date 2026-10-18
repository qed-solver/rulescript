package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
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

class FoldIsNotNullTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldIsNotNull.json"));

    @Test
    void foldsIsNotNullOfNullLiteralToFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var nullInt = Literal$.MODULE$.create(null, IntegerType$.MODULE$);
        var plan = new Filter(new IsNotNull(nullInt), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(Literal$.MODULE$.create(false, BooleanType$.MODULE$), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesIsNullOfNullLiteralAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var nullInt = Literal$.MODULE$.create(null, IntegerType$.MODULE$);
        var plan = new Filter(new IsNull(nullInt), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNotNullOfAttributeAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new IsNotNull(x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNotNullOfNonNullLiteralAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var two = Literal$.MODULE$.apply(2);
        var plan = new Filter(new IsNotNull(two), base);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNonNullIsNullTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNonNullIsNull.json"));

    @Test
    void foldsIsNullOnANonNullColumnToFalse() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new IsNull(x), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(Literal$.MODULE$.FalseLiteral(), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertFalse(Support.run(base).isEmpty());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesIsNullOnANullableColumnAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new IsNull(x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNotNullOnANonNullColumnAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new IsNotNull(x), base);
        assertEquals(plan, rule.apply(plan));
    }
}

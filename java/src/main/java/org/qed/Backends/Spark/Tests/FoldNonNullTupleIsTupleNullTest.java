package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNonNullTupleIsTupleNullTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNonNullTupleIsTupleNull.json"));

    @Test
    void foldsIsTupleNullWhenFirstElementIsNonNull() {
        var base = Support.relation("t", 2, List.of(0), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var cond = new And(new IsNull(attrs.get(0)), new IsNull(attrs.get(1)));
        var plan = new Filter(cond, base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && f.condition().equals(Literal$.MODULE$.FalseLiteral()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
        assertTrue(Support.run(rewritten).isEmpty());
    }

    @Test
    void leavesTupleNullAloneWhenFirstElementIsNullable() {
        var base = Support.relation("t", 2, List.of(), new Random(2));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new And(new IsNull(attrs.get(0)), new IsNull(attrs.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionAloneWhenFirstElementIsNonNull() {
        var base = Support.relation("t", 2, List.of(0), new Random(3));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new Or(new IsNull(attrs.get(0)), new IsNull(attrs.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

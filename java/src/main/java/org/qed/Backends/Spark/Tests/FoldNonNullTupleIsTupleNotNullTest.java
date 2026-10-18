package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNonNullTupleIsTupleNotNullTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNonNullTupleIsTupleNotNull.json"));

    @Test
    void foldsIsTupleNotNullOverNonNullTupleToTrue() {
        var base = Support.relation("Source", 2, List.of(0, 1), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var condition = new And(new IsNotNull(attrs.get(0)), new IsNotNull(attrs.get(1)));
        var plan = new Filter(condition, base);
        var rewritten = rule.apply(plan);
        var expected = new Filter(Literal$.MODULE$.TrueLiteral(), base);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTupleCheckAloneWhenAnElementIsNullable() {
        var base = Support.relation("Source", 2, List.of(0), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new And(new IsNotNull(attrs.get(0)), new IsNotNull(attrs.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionOfNotNullChecksAlone() {
        var base = Support.relation("Source", 2, List.of(0, 1), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new Or(new IsNotNull(attrs.get(0)), new IsNotNull(attrs.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

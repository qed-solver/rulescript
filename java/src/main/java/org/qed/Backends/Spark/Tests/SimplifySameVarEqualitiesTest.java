package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.types.BooleanType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifySameVarEqualitiesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifySameVarEqualities.json"));

    @Test
    void rewritesSelfEqualityIntoNotNullOrNull() {
        for (int seed = 1; seed <= 3; seed++) {
            var base = Support.relation("t", 1, List.of(), new Random(seed));
            var x = Catalyst.<Attribute>list(base.output()).get(0);
            var plan = new Filter(new EqualTo(x, x), base);
            var expected = new Filter(new Or(new IsNotNull(x), new Literal(null, BooleanType$.MODULE$)), base);
            var rewritten = rule.apply(plan);
            assertNotEquals(plan, rewritten);
            assertEquals(expected, rewritten);
            Assumptions.assumeTrue(Support.canExecute());
            assertEquals(Support.run(plan), Support.run(rewritten));
        }
    }

    @Test
    void leavesEqualityBetweenDifferentColumnsAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new EqualTo(attrs.get(0), attrs.get(1)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesStrictInequalityOnSameColumnAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new LessThan(x, x), base);
        assertEquals(plan, rule.apply(plan));
    }
}

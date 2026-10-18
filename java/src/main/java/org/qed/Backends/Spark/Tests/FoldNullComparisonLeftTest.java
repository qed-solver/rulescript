package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.IntegerType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FoldNullComparisonLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNullComparisonLeft.json"));

    @Test
    void nullEqualsColumnBecomesNullFilter() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(new Literal(null, IntegerType$.MODULE$), x), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(new Filter(new Literal(null, BooleanType$.MODULE$), base), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void nullEqualsNonNullColumnBecomesNullFilter() {
        var base = Support.relation("u", 1, List.of(0), new Random(7));
        var y = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(new Literal(null, IntegerType$.MODULE$), y), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(new Filter(new Literal(null, BooleanType$.MODULE$), base), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void nullEqualsColumnOfWiderRelationIsLeftAlone() {
        var base = Support.relation("w", 2, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(new Literal(null, IntegerType$.MODULE$), x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void columnEqualsNullOnRightIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(x, new Literal(null, IntegerType$.MODULE$)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nullGreaterThanColumnIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(new Literal(null, IntegerType$.MODULE$), x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nonNullLiteralEqualsColumnIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(Literal.create(2, IntegerType$.MODULE$), x), base);
        assertEquals(plan, rule.apply(plan));
    }
}

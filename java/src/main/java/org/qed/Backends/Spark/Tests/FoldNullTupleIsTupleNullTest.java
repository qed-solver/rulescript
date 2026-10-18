package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
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

class FoldNullTupleIsTupleNullTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNullTupleIsTupleNull.json"));

    private static Literal nullInt() {
        return new Literal(null, IntegerType$.MODULE$);
    }

    @Test
    void foldsNullIsNullConjunctionIntoTrue() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var cond = new And(new IsNull(nullInt()), new IsNull(nullInt()));
        var plan = new Filter(cond, base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child().equals(base) && f.condition().equals(Literal$.MODULE$.TrueLiteral()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void foldsWithSameShapeOverDifferentBaseRelation() {
        var base = Support.relation("u", 1, List.of(0), new Random(7));
        var cond = new And(new IsNull(nullInt()), new IsNull(nullInt()));
        var plan = new Filter(cond, base);
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof Filter f && f.condition().equals(Literal$.MODULE$.TrueLiteral()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void singleIsNullIsNotAConjunctionOfTwo() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new IsNull(nullInt()), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void isNotNullConjunctionDoesNotFold() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new And(new IsNotNull(nullInt()), new IsNotNull(nullInt())), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nonNullLiteralElementsDoNotFold() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var two = Literal$.MODULE$.apply(2);
        var plan = new Filter(new And(new IsNull(two), new IsNull(two)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void threeConjunctsDoNotFold() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var cond = new And(new And(new IsNull(nullInt()), new IsNull(nullInt())), new IsNull(nullInt()));
        var plan = new Filter(cond, base);
        assertEquals(plan, rule.apply(plan));
    }
}

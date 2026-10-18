package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterReduceExpressionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterReduceExpressions.json"));

    @Test
    void falseFilterOverScanBecomesEmptyWithSameOutput() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(Literal$.MODULE$.FalseLiteral(), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation lr && lr.data().isEmpty());
        assertEquals(base.output(), rewritten.output());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void falseFilterOverNestedFilterAlsoBecomesEmpty() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(Literal$.MODULE$.FalseLiteral(), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation lr && lr.data().isEmpty());
        assertEquals(inner.output(), rewritten.output());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void trueFilterIsNotReduced() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(Literal$.MODULE$.TrueLiteral(), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nonConstantFilterIsNotReduced() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void falseFilterOverTwoColumnScanIsNotReduced() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var plan = new Filter(Literal$.MODULE$.FalseLiteral(), base);
        assertEquals(plan, rule.apply(plan));
    }
}

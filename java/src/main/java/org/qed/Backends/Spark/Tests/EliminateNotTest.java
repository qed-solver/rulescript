package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateNotTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateNot.json"));

    @Test
    void removesDoubleNegationFromFilter() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var predicate = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new Not(new Not(predicate)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(predicate, ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void removesDoubleNegationOfNullTestOnNullableColumn() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var predicate = new IsNull(x);
        var plan = new Filter(new Not(new Not(predicate)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(predicate, ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesSingleNegationAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDoubleNegationInsideConjunctionAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var doubleNegated = new Not(new Not(new GreaterThan(x, Literal$.MODULE$.apply(2))));
        var plan = new Filter(new And(doubleNegated, new IsNull(x)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

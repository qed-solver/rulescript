package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyFalseOrTest {
    private final org.qed.spark.RuleScriptRule rule = org.qed.spark.RuleScriptRule.load(Support.generated().resolve("SimplifyFalseOr.json"));

    @Test
    void dropsFalseDisjunctAndKeepsRight() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var right = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new Or(Literal$.MODULE$.apply(false), right), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter);
        var filter = (Filter) rewritten;
        assertEquals(right, filter.condition());
        assertTrue(filter.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void dropsFalseDisjunctOnNullableColumn() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var right = new GreaterThan(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new Or(Literal$.MODULE$.apply(false), right), base);
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof Filter);
        assertEquals(right, ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTrueDisjunctAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Or(Literal$.MODULE$.apply(true), new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFalseOnTheRightAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Or(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.apply(false)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFalseDisjunctNestedInConjunctionAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(5));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Or(Literal$.MODULE$.apply(false), new GreaterThan(x, Literal$.MODULE$.apply(2)));
        var plan = new Filter(new And(inner, new GreaterThan(x, Literal$.MODULE$.apply(0))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

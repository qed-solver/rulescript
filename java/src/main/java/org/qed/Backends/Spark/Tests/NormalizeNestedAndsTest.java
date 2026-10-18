package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
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

class NormalizeNestedAndsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("NormalizeNestedAnds.json"));

    @Test
    void rightNestedAndBecomesLeftNested() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(0));
        var q = new LessThan(x, Literal$.MODULE$.apply(3));
        var r = new EqualTo(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new And(p, new And(q, r)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child().equals(base)
                && f.condition().equals(new And(new And(p, q), r)));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leftNestedAndIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(0));
        var q = new LessThan(x, Literal$.MODULE$.apply(3));
        var r = new EqualTo(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new And(new And(p, q), r), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void twoConjunctsAreLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(0));
        var q = new LessThan(x, Literal$.MODULE$.apply(3));
        var plan = new Filter(new And(p, q), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void disjunctionIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(0));
        var q = new LessThan(x, Literal$.MODULE$.apply(3));
        var r = new EqualTo(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new Or(p, new And(q, r)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

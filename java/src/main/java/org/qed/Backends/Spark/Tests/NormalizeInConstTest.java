package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizeInConstTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("NormalizeInConst.json"));

    @Test
    void dropsRepeatedDisjunctsOfAnIn() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var x = cols.get(0);
        var a = cols.get(1);
        var b = cols.get(2);
        var xa = new EqualTo(x, a);
        var xb = new EqualTo(x, b);
        var plan = new Filter(new Or(new Or(new Or(xa, xb), xa), xb), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(new Or(xa, xb), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesADisjunctOnAnotherColumnAlone() {
        var base = Support.relation("t", 4, List.of(), new Random(2));
        var cols = Catalyst.<Attribute>list(base.output());
        var x = cols.get(0);
        var a = cols.get(1);
        var b = cols.get(2);
        var c = cols.get(3);
        var xa = new EqualTo(x, a);
        var xb = new EqualTo(x, b);
        var xc = new EqualTo(x, c);
        var plan = new Filter(new Or(new Or(new Or(xa, xb), xa), xc), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesADisjunctWithAnotherOperatorAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(3));
        var cols = Catalyst.<Attribute>list(base.output());
        var x = cols.get(0);
        var a = cols.get(1);
        var b = cols.get(2);
        var xa = new EqualTo(x, a);
        var xb = new EqualTo(x, b);
        var plan = new Filter(new Or(new Or(new Or(xa, xb), xa), new GreaterThan(x, b)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

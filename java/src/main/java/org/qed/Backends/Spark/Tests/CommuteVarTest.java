package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.LessThan;
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

class CommuteVarTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CommuteVar.json"));

    @Test
    void swapsNonVariableOperandToTheRight() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var e = Add$.MODULE$.apply(x, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
        var plan = new Filter(new EqualTo(e, x), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(new EqualTo(x, e), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void swapsConstantOperandToTheRight() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var constant = Literal$.MODULE$.apply(2);
        var plan = new Filter(new EqualTo(constant, x), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(new EqualTo(x, constant), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAlreadyVariableLeftOperandAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var e = Add$.MODULE$.apply(x, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
        var plan = new Filter(new EqualTo(x, e), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNegatedEqualityAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var e = Add$.MODULE$.apply(x, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
        var plan = new Filter(new Not(new EqualTo(e, x)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOtherComparisonAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(5));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var e = Add$.MODULE$.apply(x, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
        var plan = new Filter(new LessThan(e, x), base);
        assertEquals(plan, rule.apply(plan));
    }
}

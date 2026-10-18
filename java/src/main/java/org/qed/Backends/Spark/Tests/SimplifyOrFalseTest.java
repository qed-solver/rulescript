package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
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

class SimplifyOrFalseTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyOrFalse.json"));

    @Test
    void dropsFalseFromFilterOr() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new Or(p, Literal$.MODULE$.FalseLiteral()), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition().equals(p));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOrWithoutFalseAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Or(new GreaterThan(x, Literal$.MODULE$.apply(2)),
                new GreaterThan(x, Literal$.MODULE$.apply(0))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOrNestedInsideAndAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var or = new Or(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.FalseLiteral());
        var plan = new Filter(new And(or, new GreaterThan(x, Literal$.MODULE$.apply(0))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EliminateSelectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateSelect.json"));

    @Test
    void dropsTrueFilterOverRelation() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(Literal$.MODULE$.apply(true), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(LocalRelation.class, rewritten);
        assertEquals(base, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void dropsTrueFilterOverNonNullRelation() {
        var base = Support.relation("u", 1, List.of(0), new Random(7));
        var plan = new Filter(Literal$.MODULE$.apply(true), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(base, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void dropsTrueFilterOverFilter() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Filter(Literal$.MODULE$.apply(true), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(inner, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesConditionalFilterAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTrueFilterOverTwoColumnsAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var plan = new Filter(Literal$.MODULE$.apply(true), base);
        assertEquals(plan, rule.apply(plan));
    }
}

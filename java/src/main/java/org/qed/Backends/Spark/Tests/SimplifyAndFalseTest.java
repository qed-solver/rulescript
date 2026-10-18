package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNull;
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

class SimplifyAndFalseTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyAndFalse.json"));

    @Test
    void replacesConjunctionWithFalseByFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var condition = new And(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.FalseLiteral());
        var plan = new Filter(condition, base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && f.condition().equals(Literal$.MODULE$.FalseLiteral()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void replacesConjunctionWithFalseOnNullableRowsByFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(11));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var condition = new And(new IsNull(x), Literal$.MODULE$.FalseLiteral());
        var plan = new Filter(condition, base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && f.condition().equals(Literal$.MODULE$.FalseLiteral()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesConjunctionWithTrueAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var condition = new And(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.TrueLiteral());
        var plan = new Filter(condition, base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDisjunctionWithFalseAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(7));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var condition = new Or(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.FalseLiteral());
        var plan = new Filter(condition, base);
        assertEquals(plan, rule.apply(plan));
    }
}

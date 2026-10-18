package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyTrueOrTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyTrueOr.json"));

    @Test
    void replacesTrueOrPredicateWithTrue() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Or(Literal$.MODULE$.TrueLiteral(), new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition().equals(Literal$.MODULE$.TrueLiteral()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsTrueOrPredicateWhenTrueIsSecondOperand() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Or(new GreaterThan(x, Literal$.MODULE$.apply(2)), Literal$.MODULE$.TrueLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFalseOrPredicateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(5));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Or(Literal$.MODULE$.FalseLiteral(), new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTrueAndPredicateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(6));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new And(Literal$.MODULE$.TrueLiteral(), new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

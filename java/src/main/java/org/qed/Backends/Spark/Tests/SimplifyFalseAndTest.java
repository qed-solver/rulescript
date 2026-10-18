package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.types.BooleanType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyFalseAndTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyFalseAnd.json"));

    @Test
    void replacesFalseAndPredicateWithFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var falseLiteral = Literal$.MODULE$.create(false, BooleanType$.MODULE$);
        var pred = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new And(falseLiteral, pred), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition().equals(falseLiteral));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFalseOrPredicateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var falseLiteral = Literal$.MODULE$.create(false, BooleanType$.MODULE$);
        var pred = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new Or(falseLiteral, pred), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesWiderInputAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var falseLiteral = Literal$.MODULE$.create(false, BooleanType$.MODULE$);
        var pred = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new And(falseLiteral, pred), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTrueAndPredicateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var trueLiteral = Literal$.MODULE$.create(true, BooleanType$.MODULE$);
        var pred = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new And(trueLiteral, pred), base);
        assertEquals(plan, rule.apply(plan));
    }
}

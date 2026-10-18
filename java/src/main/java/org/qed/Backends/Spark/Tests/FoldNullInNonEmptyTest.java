package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.IntegerType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNullInNonEmptyTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNullInNonEmpty.json"));

    @Test
    void foldsNullInstancesOfTwoElementTupleToNullFilter() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var nullInt = new Literal(null, IntegerType$.MODULE$);
        var cond = new Or(new EqualTo(nullInt, attrs.get(0)), new EqualTo(nullInt, attrs.get(1)));
        var plan = new Filter(cond, base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base
                && f.condition() instanceof Literal l && l.value() == null && l.dataType().equals(BooleanType$.MODULE$));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesThreeElementDisjunctionAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var nullInt = new Literal(null, IntegerType$.MODULE$);
        var cond = new Or(new EqualTo(nullInt, attrs.get(0)),
                new Or(new EqualTo(nullInt, attrs.get(1)), new EqualTo(nullInt, attrs.get(0))));
        var plan = new Filter(cond, base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotInConjunctionAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var nullInt = new Literal(null, IntegerType$.MODULE$);
        var cond = new And(new Not(new EqualTo(nullInt, attrs.get(0))), new Not(new EqualTo(nullInt, attrs.get(1))));
        var plan = new Filter(cond, base);
        assertEquals(plan, rule.apply(plan));
    }
}

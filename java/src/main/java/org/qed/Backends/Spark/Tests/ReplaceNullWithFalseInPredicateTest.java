package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
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

class ReplaceNullWithFalseInPredicateTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ReplaceNullWithFalseInPredicate.json"));

    @Test
    void replacesNullBooleanConjunctWithFalse() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var nullBool = Literal$.MODULE$.create(null, BooleanType$.MODULE$);
        var plan = new Filter(new And(p, nullBool), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter, "result is not a Filter: " + rewritten);
        var filter = (Filter) rewritten;
        assertEquals(base, filter.child());
        assertTrue(filter.condition() instanceof And, "condition is not an And: " + filter.condition());
        var cond = (And) filter.condition();
        assertEquals(p, cond.left());
        assertTrue(cond.right() instanceof Literal lit && Boolean.FALSE.equals(lit.value())
                && lit.dataType().equals(BooleanType$.MODULE$), "right arm is not FALSE: " + cond.right());
        if (!Support.canExecute()) {
            Assumptions.abort("no Spark session in this sandbox");
        }
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNonBooleanNullConjunctAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var nullInt = Literal$.MODULE$.create(null, IntegerType$.MODULE$);
        var plan = new Filter(new And(p, nullInt), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullUnderOrAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var nullBool = Literal$.MODULE$.create(null, BooleanType$.MODULE$);
        var plan = new Filter(new Or(p, nullBool), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonNullBooleanConjunctAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var p = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(new And(p, Literal$.MODULE$.TrueLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }
}

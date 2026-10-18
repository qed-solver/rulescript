package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.types.BooleanType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifySameVarInequalitiesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifySameVarInequalities.json"));

    @Test
    void rewritesSameVariableNotEqualToNullCheck() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(new EqualTo(x, x)), base);
        var expected = new Filter(new And(new IsNull(x), new Literal(null, BooleanType$.MODULE$)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void rewritesSameVariableNotEqualOnNonNullColumn() {
        var base = Support.relation("t", 1, List.of(0), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(new EqualTo(x, x)), base);
        var rewritten = rule.apply(plan);
        assertTrue(rewritten instanceof Filter f && f.child() == base && f.condition() instanceof And);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesEqualityOnSameVariableAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new EqualTo(x, x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesInequalityOfDifferentVariablesAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(4));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Filter(new Not(new EqualTo(columns.get(0), columns.get(1))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotEqualToLiteralAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(5));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(new EqualTo(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

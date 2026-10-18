package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNotNull;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNonNullIsNotNullTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNonNullIsNotNull.json"));

    @Test
    void foldsIsNotNullOfANonNullColumnToTrue() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new IsNotNull(x), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base && Catalyst.isTrue(f.condition()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesIsNotNullOfANullableColumnAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new IsNotNull(x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIsNullOfANonNullColumnAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new IsNull(x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void foldsIsNotNullOverANonNullProjection() {
        var base = Support.relation("t", 1, List.of(0), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var projected = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        var plan = new Filter(new IsNotNull(x), projected);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == projected && Catalyst.isTrue(f.condition()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAConjunctionContainingIsNotNullAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new And(new IsNotNull(x), new GreaterThan(x, Literal$.MODULE$.apply(1))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

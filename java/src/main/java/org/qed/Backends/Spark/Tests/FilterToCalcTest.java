package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
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

class FilterToCalcTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterToCalc.json"));

    @Test
    void filterBecomesFilterUnderProjection() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var cond = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var plan = new Filter(cond, base);
        var expected = new Project(Catalyst.<NamedExpression>seq(List.of(x)), new Filter(cond, base));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Filter f && f.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOverTwoColumnsAlone() {
        var base = Support.relation("u", 2, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesBareRelationAlone() {
        var base = Support.relation("v", 1, List.of(), new Random(3));
        assertEquals(base, rule.apply(base));
    }
}

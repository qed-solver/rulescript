package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
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

class PushLeakproofFiltersIntoPermeableBarrierTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("PushLeakproofFiltersIntoPermeableBarrier.json"));

    @Test
    void pushesTheLeakproofConjunctBelowTheBarrier() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var barrier = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        var leak = new GreaterThan(x, Literal$.MODULE$.apply(1));
        var remaining = new LessThan(x, Literal$.MODULE$.apply(3));
        var plan = new Filter(new And(leak, remaining), barrier);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter top && top.child() instanceof Project above
                && above.child() instanceof Filter below && below.child() == base);
        var top = (Filter) rewritten;
        var above = (Project) top.child();
        var below = (Filter) above.child();
        assertEquals(remaining, top.condition());
        assertEquals(leak, below.condition());
        assertEquals(List.of(x), Catalyst.list(above.projectList()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesASingleConjunctAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var barrier = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), barrier);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAFilterWithoutTheBarrierAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(1)),
                new LessThan(x, Literal$.MODULE$.apply(3))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANonIdentityBarrierAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(3));
        var second = Catalyst.<Attribute>list(base.output()).get(1);
        var barrier = new Project(Catalyst.<NamedExpression>seq(List.of(second)), base);
        var plan = new Filter(new And(new GreaterThan(second, Literal$.MODULE$.apply(1)),
                new LessThan(second, Literal$.MODULE$.apply(3))), barrier);
        assertEquals(plan, rule.apply(plan));
    }
}

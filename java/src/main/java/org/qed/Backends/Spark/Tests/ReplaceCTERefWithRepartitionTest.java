package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaceCTERefWithRepartitionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ReplaceCTERefWithRepartition.json"));

    @Test
    void wrapsBareRelationInIdentityProject() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var rewritten = rule.apply(base);
        assertNotEquals(base, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() == base);
        assertEquals(Catalyst.list(base.output()), Catalyst.list(((Project) rewritten).projectList()));
        assertEquals(Catalyst.list(base.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(base), Support.run(rewritten));
    }

    @Test
    void wrapsRelationUnderFilter() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.child() instanceof Filter f
                && f.child() instanceof Project inner && inner.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesRelationOfOtherArityAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(3));
        assertEquals(base, rule.apply(base));
    }

    @Test
    void leavesFilterOverOtherArityAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(1)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConvertToCatalystTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ConvertToCatalyst.json"));

    @Test
    void widensTheSharedArgumentAndFiltersOnce() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var shared = new Add(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new GreaterThan(shared, shared), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var outer = assertInstanceOf(Project.class, rewritten);
        assertEquals(1, Catalyst.list(outer.projectList()).size());
        var filter = assertInstanceOf(Filter.class, outer.child());
        var widened = assertInstanceOf(Project.class, filter.child());
        assertEquals(2, Catalyst.list(widened.projectList()).size());
        assertEquals(base, widened.child());
        var condition = assertInstanceOf(GreaterThan.class, filter.condition());
        assertEquals(condition.left(), condition.right());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesDifferentSharedArgumentsAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(new Add(x, Literal$.MODULE$.apply(1)),
                new Add(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAWiderChildAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var shared = new Add(x, Literal$.MODULE$.apply(1));
        var plan = new Filter(new GreaterThan(shared, shared), base);
        assertEquals(plan, rule.apply(plan));
    }
}

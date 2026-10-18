package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushLeakproofProjectionsIntoPermeableBarrierTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("PushLeakproofProjectionsIntoPermeableBarrier.json"));

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression plusOne(Attribute x) {
        return Add$.MODULE$.apply(x, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
    }

    @Test
    void pushesProjectionBelowIdentityBarrier() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var barrier = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        var computed = named(plusOne(x), "e");
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(computed, x)), barrier);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        assertEquals(2, Catalyst.list(top.projectList()).size());
        assertTrue(top.child() instanceof Project);
        var below = (Project) top.child();
        assertTrue(below.child() == base);
        var belowList = Catalyst.<NamedExpression>list(below.projectList());
        assertEquals(2, belowList.size());
        assertTrue(belowList.get(0) instanceof Alias);
        assertEquals(plusOne(x), ((Alias) belowList.get(0)).child());
        assertEquals(x, belowList.get(1));
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectionOverWideBarrierAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(2));
        var columns = Catalyst.<Attribute>list(base.output());
        var x = columns.get(0);
        var barrier = new Project(Catalyst.<NamedExpression>seq(List.of(columns.get(0), columns.get(1))), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(named(plusOne(x), "e"), x)), barrier);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionWithoutBarrierAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(named(plusOne(x), "e"), x)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneWithColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneWithCols.json"));

    private static Alias plusOne(Attribute x, String name) {
        return Alias$.MODULE$.apply(Add$.MODULE$.apply(x, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3()),
                name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()), scala.Option.empty(),
                Catalyst.<String>seq(List.of()));
    }

    @Test
    void pushesProjectionBelowIdentityWith() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var identity = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        var e = plusOne(x, "e");
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(e, x)), identity);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var outer = (Project) rewritten;
        assertTrue(outer.child() instanceof Project);
        var below = (Project) outer.child();
        assertTrue(below.child() == base);
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        var belowItems = Catalyst.list(below.projectList());
        assertEquals(2, belowItems.size());
        assertTrue(belowItems.get(0) instanceof Alias);
        assertTrue(((Alias) belowItems.get(0)).child() instanceof org.apache.spark.sql.catalyst.expressions.Add);
        assertEquals(x, belowItems.get(1));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectionWithoutIdentityWithAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var e = plusOne(x, "e");
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(e, x)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionOverNonPassthroughChildAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var y = plusOne(x, "y");
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(y)), base);
        var yAttr = y.toAttribute();
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(plusOne(yAttr, "e"), yAttr)), inner);
        assertEquals(plan, rule.apply(plan));
    }
}

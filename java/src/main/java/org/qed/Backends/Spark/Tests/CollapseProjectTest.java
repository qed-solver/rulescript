package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Subtract$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollapseProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CollapseProject.json"));

    private static Alias alias(Expression value, String name) {
        return (Alias) Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression plus(Expression a, Expression b) {
        return Add$.MODULE$.apply(a, b, Add$.MODULE$.apply$default$3());
    }

    private static Expression minus(Expression a, Expression b) {
        return Subtract$.MODULE$.apply(a, b, Subtract$.MODULE$.apply$default$3());
    }

    @Test
    void collapsesTwoProjectsIntoOne() {
        var base = Support.relation("s", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var x = cols.get(0);
        var e0 = alias(plus(x, Literal$.MODULE$.apply(10)), "e0");
        var lower = new Project(Catalyst.<NamedExpression>seq(List.of(e0, x)), base);
        var low = Catalyst.<Attribute>list(lower.output());
        var c0 = low.get(0);
        var c1 = low.get(1);
        var p0 = alias(plus(c0, c1), "p0");
        var p1 = alias(minus(c0, c1), "p1");
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(p0, p1)), lower);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var out = (Project) rewritten;
        assertSame(base, out.child());
        var items = Catalyst.<NamedExpression>list(out.projectList());
        assertEquals(2, items.size());
        assertEquals("p0", items.get(0).name());
        assertEquals("p1", items.get(1).name());
        var inlined = plus(x, Literal$.MODULE$.apply(10));
        assertEquals(plus(inlined, x), ((Alias) items.get(0)).child());
        assertEquals(minus(inlined, x), ((Alias) items.get(1)).child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectsAloneWhenPassThroughIsNotFirstColumn() {
        var base = Support.relation("s", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var x = cols.get(0);
        var y = cols.get(1);
        var e0 = alias(plus(x, Literal$.MODULE$.apply(10)), "e0");
        var lower = new Project(Catalyst.<NamedExpression>seq(List.of(e0, y)), base);
        var low = Catalyst.<Attribute>list(lower.output());
        var p0 = alias(plus(low.get(0), low.get(1)), "p0");
        var p1 = alias(minus(low.get(0), low.get(1)), "p1");
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(p0, p1)), lower);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectsAloneWhenLowerHasNoPassThroughColumn() {
        var base = Support.relation("s", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var x = cols.get(0);
        var y = cols.get(1);
        var e0 = alias(plus(x, Literal$.MODULE$.apply(10)), "e0");
        var e1 = alias(plus(y, Literal$.MODULE$.apply(1)), "e1");
        var lower = new Project(Catalyst.<NamedExpression>seq(List.of(e0, e1)), base);
        var low = Catalyst.<Attribute>list(lower.output());
        var p0 = alias(plus(low.get(0), low.get(1)), "p0");
        var p1 = alias(minus(low.get(0), low.get(1)), "p1");
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(p0, p1)), lower);
        assertEquals(plan, rule.apply(plan));
    }
}

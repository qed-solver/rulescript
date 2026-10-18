package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Coalesce;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.If;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalcMergeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CalcMerge.json"));

    private static Expression bCond(Expression a, Expression b) {
        return new GreaterThan(a, b);
    }

    private static Expression b1(Expression a, Expression b) {
        return new Coalesce(Catalyst.<Expression>seq(List.of(a, b)));
    }

    private static Expression b2(Expression a, Expression b) {
        return new If(new GreaterThan(a, b), a, b);
    }

    private static Expression tCond(Expression a, Expression b) {
        return new LessThan(a, b);
    }

    private static Expression t1(Expression a, Expression b) {
        return new Coalesce(Catalyst.<Expression>seq(List.of(b, a)));
    }

    private static Expression t2(Expression a, Expression b) {
        return new If(new GreaterThan(a, b), a, b);
    }

    private static NamedExpression named(Expression e, String name) {
        return Alias$.MODULE$.apply(e, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Project bottom(LogicalPlan base, Attribute x, Attribute y) {
        var filter = new Filter(bCond(x, y), base);
        return new Project(Catalyst.<NamedExpression>seq(List.of(named(b1(x, y), "b1"), named(b2(x, y), "b2"))), filter);
    }

    @Test
    void mergesCalcOnTopOfCalc() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var x = cols.get(0);
        var y = cols.get(1);
        var lower = bottom(base, x, y);
        var lowerOut = Catalyst.<Attribute>list(lower.output());
        var c0 = lowerOut.get(0);
        var c1 = lowerOut.get(1);
        var topFilter = new Filter(tCond(c0, c1), lower);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(named(t1(c0, c1), "t1"), named(t2(c0, c1), "t2"))), topFilter);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var merged = (Project) rewritten;
        assertTrue(merged.child() instanceof Filter);
        var filter = (Filter) merged.child();
        assertSame(base, filter.child());
        assertTrue(new And(bCond(x, y), tCond(b1(x, y), b2(x, y))).semanticEquals(filter.condition()));
        var items = Catalyst.<NamedExpression>list(merged.projectList());
        assertTrue(((Alias) items.get(0)).child().semanticEquals(t1(b1(x, y), b2(x, y))));
        assertTrue(((Alias) items.get(1)).child().semanticEquals(t2(b1(x, y), b2(x, y))));

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectWithoutTopFilterAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var lower = bottom(base, cols.get(0), cols.get(1));
        var lowerOut = Catalyst.<Attribute>list(lower.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                named(t1(lowerOut.get(0), lowerOut.get(1)), "t1"),
                named(t2(lowerOut.get(0), lowerOut.get(1)), "t2"))), lower);

        assertEquals(plan, rule.apply(plan));
    }
}

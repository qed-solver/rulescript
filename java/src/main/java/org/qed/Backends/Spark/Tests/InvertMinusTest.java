package org.qed.spark;

import java.util.List;
import java.util.Random;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Subtract;
import org.apache.spark.sql.catalyst.expressions.Subtract$;
import org.apache.spark.sql.catalyst.expressions.UnaryMinus$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvertMinusTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InvertMinus.json"));

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression minus(Attribute left, Attribute right) {
        return Subtract$.MODULE$.apply(left, right, Subtract$.MODULE$.apply$default$3());
    }

    private static Expression negate(Expression value) {
        return UnaryMinus$.MODULE$.apply(value, UnaryMinus$.MODULE$.apply$default$2());
    }

    @Test
    void invertsNegatedDifference() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var a = cols.get(0);
        var b = cols.get(1);
        var plan = new Project(Catalyst.seq(List.of(named(negate(minus(a, b)), "c"))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var outer = (Project) rewritten;
        assertTrue(Catalyst.list(outer.projectList()).get(0) instanceof Alias);
        assertTrue(outer.child() instanceof Project);
        var inner = (Project) outer.child();
        assertTrue(Catalyst.list(inner.projectList()).get(0) instanceof Alias);
        var alias = (Alias) Catalyst.list(inner.projectList()).get(0);
        assertTrue(alias.child() instanceof Subtract);
        var s = (Subtract) alias.child();
        assertEquals(b, s.left());
        assertEquals(a, s.right());
        assertTrue(inner.child() == base);
        assertEquals(Catalyst.list(plan.output()).get(0).exprId(),
                Catalyst.list(rewritten.output()).get(0).exprId());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesPlainDifferenceAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.seq(List.of(named(minus(cols.get(0), cols.get(1)), "c"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNegatedSameColumnAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var a = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.seq(List.of(named(negate(minus(a, a)), "c"))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

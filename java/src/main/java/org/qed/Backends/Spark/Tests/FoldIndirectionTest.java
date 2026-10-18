package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
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

class FoldIndirectionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldIndirection.json"));

    private static NamedExpression named(Expression e, String name) {
        return Alias$.MODULE$.apply(e, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void foldsIndirectionIntoTheReferencedElement() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var e0 = new GreaterThan(x, Literal$.MODULE$.apply(2));
        var e1 = new LessThan(x, Literal$.MODULE$.apply(15));
        var inner = new Project(Catalyst.seq(List.of(named(e0, "e0"), named(e1, "e1"))), base);
        var second = Catalyst.<Attribute>list(inner.output()).get(1);
        var plan = new Project(Catalyst.seq(List.of(second)), inner);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var result = (Project) rewritten;
        assertTrue(result.child() == base);
        var items = Catalyst.<NamedExpression>list(result.projectList());
        assertEquals(1, items.size());
        Expression body = items.get(0) instanceof Alias a ? a.child() : (Expression) items.get(0);
        assertEquals(e1, body);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesIndirectionOntoTheFirstElementAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Project(Catalyst.seq(List.of(
                named(new GreaterThan(x, Literal$.MODULE$.apply(2)), "e0"),
                named(new LessThan(x, Literal$.MODULE$.apply(15)), "e1"))), base);
        var first = Catalyst.<Attribute>list(inner.output()).get(0);
        var plan = new Project(Catalyst.seq(List.of(first)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesIndirectionIntoAThreeElementArrayAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Project(Catalyst.seq(List.of(
                named(new GreaterThan(x, Literal$.MODULE$.apply(2)), "e0"),
                named(new LessThan(x, Literal$.MODULE$.apply(15)), "e1"),
                named(new GreaterThan(x, Literal$.MODULE$.apply(7)), "e2"))), base);
        var second = Catalyst.<Attribute>list(inner.output()).get(1);
        var plan = new Project(Catalyst.seq(List.of(second)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectionOverABaseRelationAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.seq(List.of(x)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

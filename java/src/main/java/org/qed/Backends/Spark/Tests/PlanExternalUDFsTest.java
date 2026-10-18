package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.MonotonicallyIncreasingID;
import org.apache.spark.sql.catalyst.expressions.Multiply;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlanExternalUDFsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PlanExternalUDFs.json"));

    private static Alias alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void extractsUdfIntoLowerProjection() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                alias(new Add(x, Literal$.MODULE$.apply(1)), "f"),
                alias(new Multiply(x, Literal$.MODULE$.apply(2)), "g"))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var outer = assertInstanceOf(Project.class, rewritten);
        var inner = assertInstanceOf(Project.class, outer.child());
        assertSame(base, inner.child());
        assertEquals(2, inner.projectList().size());
        assertEquals(x.exprId(), assertInstanceOf(Attribute.class, inner.projectList().apply(1)).exprId());
        assertEquals(2, outer.projectList().size());
        assertInstanceOf(Multiply.class, assertInstanceOf(Alias.class, outer.projectList().apply(1)).child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTwoColumnInputAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(2));
        var columns = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                alias(new Add(columns.get(0), Literal$.MODULE$.apply(1)), "f"),
                alias(new Multiply(columns.get(1), Literal$.MODULE$.apply(2)), "g"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNondeterministicUdfAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                alias(new Add(x, new MonotonicallyIncreasingID()), "f"),
                alias(new Multiply(x, Literal$.MODULE$.apply(2)), "g"))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

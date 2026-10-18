package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Rand;
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

class ExtractPythonUDFsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ExtractPythonUDFs.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void extractsUdfIntoEvalNodeBelowProject() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var fx = new Add(x, Literal$.MODULE$.apply(1));
        var gx = new Multiply(x, Literal$.MODULE$.apply(2));
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(fx, "f"), alias(gx, "g"))), base);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);

        var chain = new java.util.ArrayList<LogicalPlan>();
        for (LogicalPlan node = rewritten; node != base; node = node.children().head()) {
            chain.add(node);
        }
        var evalNode = chain.get(chain.size() - 1);
        assertTrue(evalNode instanceof Project, "node above the input is an EvalPython projection");
        var evalItems = Catalyst.<NamedExpression>list(((Project) evalNode).projectList());
        assertEquals(2, evalItems.size());
        assertTrue(evalItems.get(0) instanceof Alias a && a.child() instanceof Add, "first item computes f(x) once");
        assertTrue(evalItems.get(1) instanceof Attribute, "second item passes x through");

        var gUses = chain.stream()
                .filter(node -> node instanceof Project)
                .flatMap(node -> Catalyst.<NamedExpression>list(((Project) node).projectList()).stream())
                .filter(item -> item instanceof Alias a && a.child() instanceof Multiply)
                .count();
        assertEquals(1, gUses, "g stays in exactly one operator above the extraction");
        assertTrue(chain.stream().noneMatch(node -> node instanceof Project p
                && Catalyst.<NamedExpression>list(p.projectList()).stream()
                        .anyMatch(item -> item instanceof Alias a && a.child() instanceof Add)
                && node != evalNode), "f(x) is no longer recomputed by the operator");

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectOverWiderInputAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var a = attrs.get(0);
        var b = attrs.get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                alias(new Add(a, b), "f"), alias(new Multiply(a, Literal$.MODULE$.apply(2)), "g"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonDeterministicUdfAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(
                alias(new Rand(Literal$.MODULE$.apply(1)), "f"), alias(new Multiply(x, Literal$.MODULE$.apply(2)), "g"))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

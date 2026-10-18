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

class GenerateOptimizationTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("GenerateOptimization.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression plus(Attribute x, int k) {
        return Add$.MODULE$.apply(x, Literal$.MODULE$.apply(k), Add$.MODULE$.apply$default$3());
    }

    @Test
    void prunesTheUnusedGeneratorOutput() {
        var base = Support.relation("b", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var jt2 = plus(x, 2);
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(alias(plus(x, 1), "jt1"), alias(jt2, "jt2"))), base);
        var out2 = Catalyst.<Attribute>list(inner.output()).get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(out2)), inner);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.child() instanceof Project);
        var pruned = (Project) ((Project) rewritten).child();
        assertEquals(1, Catalyst.list(pruned.projectList()).size());
        assertTrue(Catalyst.list(pruned.projectList()).get(0) instanceof Alias a && a.child().equals(jt2));
        assertTrue(pruned.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsAProjectOfBothGeneratorOutputs() {
        var base = Support.relation("b", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(alias(plus(x, 1), "jt1"), alias(plus(x, 2), "jt2"))), base);
        var outputs = Catalyst.<Attribute>list(inner.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(outputs.get(0), outputs.get(1))), inner);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsAProjectOverThreeGeneratorOutputs() {
        var base = Support.relation("b", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(alias(plus(x, 1), "jt1"), alias(plus(x, 2), "jt2"),
                alias(plus(x, 3), "jt3"))), base);
        var out2 = Catalyst.<Attribute>list(inner.output()).get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(out2)), inner);
        assertEquals(plan, rule.apply(plan));
    }
}

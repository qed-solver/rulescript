package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
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

class MergeProjectsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("MergeProjects.json"));

    @Test
    void mergesProjectsOverPassthroughAndSynthesizedColumns() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var bottom = alias(org.apache.spark.sql.catalyst.expressions.Add$.MODULE$.apply(x,
                Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3()), "bottom");
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(x, bottom)), base);
        var innerOutput = Catalyst.<Attribute>list(inner.output());
        var top = alias(Add$.MODULE$.apply(innerOutput.get(0), Literal$.MODULE$.apply(10),
                Add$.MODULE$.apply$default$3()), "top");
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(top)), inner);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() == base);
        assertEquals(1, Catalyst.list(((Project) rewritten).projectList()).size());
        assertEquals(Catalyst.list(plan.output()).get(0).exprId(),
                Catalyst.list(rewritten.output()).get(0).exprId());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectsAloneWhenOuterReadsSynthesizedColumn() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var bottom = alias(Add$.MODULE$.apply(x, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3()), "bottom");
        var inner = new Project(Catalyst.<NamedExpression>seq(List.of(x, bottom)), base);
        var innerOutput = Catalyst.<Attribute>list(inner.output());
        var top = alias(Add$.MODULE$.apply(innerOutput.get(1), Literal$.MODULE$.apply(10),
                Add$.MODULE$.apply$default$3()), "top");
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(top)), inner);
        assertEquals(plan, rule.apply(plan));
    }

    private static NamedExpression alias(org.apache.spark.sql.catalyst.expressions.Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateZeroCardProjectTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateZeroCardProject.json"));

    private static LocalRelation emptyRelation(List<Attribute> attrs) {
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    private static Alias projected(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void rewritesProjectOfEmptyInputToEmptyRelation() {
        var a = Support.attribute("a", true);
        var b = Support.attribute("b", true);
        var base = emptyRelation(List.of(a, b));
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(a, b,
                projected(new Add(a, Literal$.MODULE$.apply(1)), "f"))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation lr && lr.data().isEmpty());
        assertEquals(Catalyst.list(plan.output()).stream().map(Attribute::exprId).toList(),
                Catalyst.list(rewritten.output()).stream().map(Attribute::exprId).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesProjectOfNonEmptyInputAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), cols.get(1),
                projected(new Add(cols.get(0), Literal$.MODULE$.apply(1)), "f"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectOverWrongArityAlone() {
        var a = Support.attribute("a", true);
        var b = Support.attribute("b", true);
        var c = Support.attribute("c", true);
        var base = emptyRelation(List.of(a, b, c));
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(a, b,
                projected(new Add(a, Literal$.MODULE$.apply(1)), "f"))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesReorderedPassthroughAlone() {
        var a = Support.attribute("a", true);
        var b = Support.attribute("b", true);
        var base = emptyRelation(List.of(a, b));
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(b, a,
                projected(new Add(a, Literal$.MODULE$.apply(1)), "f"))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

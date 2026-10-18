package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.UnaryMinus$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateUnaryMinusTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateUnaryMinus.json"));

    private static Expression negate(Expression e) {
        return UnaryMinus$.MODULE$.apply(e, UnaryMinus$.MODULE$.apply$default$2());
    }

    private static Project projectOne(Expression value, LogicalPlan child) {
        NamedExpression item = Alias$.MODULE$.apply(value, "v", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
        return new Project(Catalyst.<NamedExpression>seq(List.of(item)), child);
    }

    @Test
    void removesDoubleNegation() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        Attribute x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = projectOne(negate(negate(x)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() == base);
        assertEquals(plan.output().head().exprId(), rewritten.output().head().exprId());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void removesDoubleNegationOnNonNullColumn() {
        var base = Support.relation("t", 1, List.of(0), new Random(7));
        Attribute x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = projectOne(negate(negate(x)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesASingleNegationAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        Attribute x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = projectOne(negate(x), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNegatedLiteralAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = projectOne(negate(Literal$.MODULE$.apply(3)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

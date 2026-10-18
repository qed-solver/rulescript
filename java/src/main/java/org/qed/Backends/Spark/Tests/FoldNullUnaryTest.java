package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.UnaryMinus$;
import org.apache.spark.sql.catalyst.expressions.UnaryPositive;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.types.IntegerType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNullUnaryTest {
    private static RuleScriptRule rule;

    @BeforeAll
    static void load() {
        rule = RuleScriptRule.load(Support.generated().resolve("FoldNullUnary.json"));
    }

    private static Literal nullInt() {
        return new Literal(null, IntegerType$.MODULE$);
    }

    private static Alias named(Expression value) {
        return new Alias(value, "neg", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void foldsMinusNullToNull() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var minusNull = UnaryMinus$.MODULE$.apply(nullInt(), false);
        var plan = new Project(Catalyst.<org.apache.spark.sql.catalyst.expressions.NamedExpression>seq(List.of(named(minusNull))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer
                && outer.child() instanceof Project inner
                && Catalyst.list(inner.projectList()).get(0) instanceof Alias a
                && a.child() instanceof Literal l
                && l.value() == null
                && l.dataType().equals(IntegerType$.MODULE$));
        assertEquals(Catalyst.<Attribute>list(plan.output()).get(0).exprId(),
                Catalyst.<Attribute>list(rewritten.output()).get(0).exprId());
        assumeExecutable();
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesMinusOfNonNullAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var minusTwo = UnaryMinus$.MODULE$.apply(Literal$.MODULE$.apply(2), false);
        var plan = new Project(Catalyst.<org.apache.spark.sql.catalyst.expressions.NamedExpression>seq(List.of(named(minusTwo))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesUnaryPlusOfNullAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plusNull = new UnaryPositive(nullInt());
        var plan = new Project(Catalyst.<org.apache.spark.sql.catalyst.expressions.NamedExpression>seq(List.of(named(plusNull))), base);
        assertEquals(plan, rule.apply(plan));
    }

    private static void assumeExecutable() {
        Assumptions.assumeTrue(Support.canExecute());
    }
}

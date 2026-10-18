package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalcReduceExpressionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("CalcReduceExpressions.json"));

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void falseFilterUnderProjectBecomesEmpty() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var filtered = new Filter(Literal$.MODULE$.FalseLiteral(), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(y, "e1"), alias(x, "e2"))), filtered);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation empty && empty.data().isEmpty());
        var expected = Catalyst.list(plan.output());
        var actual = Catalyst.list(rewritten.output());
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).dataType(), actual.get(i).dataType());
        }
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void emptyKeepsBooleanProjectedType() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var filtered = new Filter(Literal$.MODULE$.FalseLiteral(), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(new GreaterThan(y, Literal$.MODULE$.apply(1)), "e1"), alias(x, "e2"))), filtered);

        var rewritten = rule.apply(plan);

        assertTrue(rewritten instanceof LocalRelation empty && empty.data().isEmpty());
        var expected = Catalyst.list(plan.output());
        var actual = Catalyst.list(rewritten.output());
        assertEquals(expected.get(0).dataType(), actual.get(0).dataType());
        assertEquals(expected.get(1).dataType(), actual.get(1).dataType());
    }

    @Test
    void nonFalseFilterUnderProjectIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var filtered = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(y, "e1"), alias(x, "e2"))), filtered);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void trueFilterUnderProjectIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var filtered = new Filter(Literal$.MODULE$.TrueLiteral(), base);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(y, "e1"), alias(x, "e2"))), filtered);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void projectWithoutFilterIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(alias(y, "e1"), alias(x, "e2"))), base);
        assertEquals(plan, rule.apply(plan));
    }
}

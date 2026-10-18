package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateSerializationTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateSerialization.json"));

    @Test
    void filtersOnTheProjectedColumnMoveBelowTheProjection() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var deser = plus(x, 1);
        var plan = new Project(Catalyst.seq(List.of(alias(deser, "d"))),
                new Filter(new GreaterThan(deser, Literal$.MODULE$.apply(2)), base));
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() instanceof Project p && p.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void filterOnTheRawColumnIsNotRewritten() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.seq(List.of(alias(plus(x, 1), "d"))),
                new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void projectionDifferentFromTheFilteredOneIsNotRewritten() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.seq(List.of(alias(plus(x, 2), "d"))),
                new Filter(new GreaterThan(plus(x, 1), Literal$.MODULE$.apply(2)), base));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void filterWithoutProjectionAboveItIsNotRewritten() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(plus(x, 1), Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    private static Expression plus(Attribute x, int n) {
        return Add$.MODULE$.apply(x, Literal$.MODULE$.apply(n), Add$.MODULE$.apply$default$3());
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }
}

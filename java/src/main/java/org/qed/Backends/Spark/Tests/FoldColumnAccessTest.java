package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Multiply;
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

class FoldColumnAccessTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldColumnAccess.json"));

    private static Alias alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void foldsTheSecondElementOfATwoElementTupleIntoItsProjection() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var first = alias(new Add(x, Literal$.MODULE$.apply(1)), "e0");
        var second = alias(new Multiply(x, Literal$.MODULE$.apply(3)), "e1");
        var tuple = new Project(Catalyst.<NamedExpression>seq(List.of(first, second)), base);
        var element = Catalyst.<Attribute>list(tuple.output()).get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(element)), tuple);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() == base);
        assertEquals(element.exprId(), Catalyst.<NamedExpression>list(((Project) rewritten).projectList()).get(0).exprId());
        assertEquals(new Project(Catalyst.<NamedExpression>seq(List.of(second)), base), rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAThreeElementTupleAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var first = alias(new Add(x, Literal$.MODULE$.apply(1)), "e0");
        var second = alias(new Multiply(x, Literal$.MODULE$.apply(3)), "e1");
        var third = alias(new Add(x, Literal$.MODULE$.apply(2)), "e2");
        var tuple = new Project(Catalyst.<NamedExpression>seq(List.of(first, second, third)), base);
        var element = Catalyst.<Attribute>list(tuple.output()).get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(element)), tuple);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAccessToTheFirstElementAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var first = alias(new Add(x, Literal$.MODULE$.apply(1)), "e0");
        var second = alias(new Multiply(x, Literal$.MODULE$.apply(3)), "e1");
        var tuple = new Project(Catalyst.<NamedExpression>seq(List.of(first, second)), base);
        var element = Catalyst.<Attribute>list(tuple.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(element)), tuple);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAccessBehindAFilterAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var first = alias(new Add(x, Literal$.MODULE$.apply(1)), "e0");
        var second = alias(new Multiply(x, Literal$.MODULE$.apply(3)), "e1");
        var tuple = new Project(Catalyst.<NamedExpression>seq(List.of(first, second)), base);
        var filtered = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(0)), tuple);
        var element = Catalyst.<Attribute>list(filtered.output()).get(1);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(element)), filtered);
        assertEquals(plan, rule.apply(plan));
    }
}

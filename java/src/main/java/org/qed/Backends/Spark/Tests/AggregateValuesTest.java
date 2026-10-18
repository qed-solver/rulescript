package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateValuesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateValues.json"));

    @Test
    void groupingByTheOnlyColumnBecomesDistinctUnion() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(x)), Catalyst.<NamedExpression>seq(List.of(x)),
                base, Option.empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate a && a.child() instanceof Union u
                && Catalyst.list(u.children()).size() == 2
                && Catalyst.list(u.children()).get(0) == base
                && Catalyst.list(u.children()).get(1) instanceof LocalRelation right
                && right.output().size() == 1);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void groupingAFilteredInputBecomesDistinctUnion() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var filtered = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(x)), Catalyst.<NamedExpression>seq(List.of(x)),
                filtered, Option.empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate a && a.child() instanceof Union u
                && Catalyst.list(u.children()).size() == 2);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void groupingOnlyOneOfTwoColumnsIsLeftAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(3));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(x)), Catalyst.<NamedExpression>seq(List.of(x)),
                base, Option.empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void projectionOfTheOnlyColumnIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(x)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

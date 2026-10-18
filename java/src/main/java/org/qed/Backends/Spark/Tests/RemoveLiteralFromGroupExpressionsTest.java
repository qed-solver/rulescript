package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.aggregate.Sum;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoveLiteralFromGroupExpressionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RemoveLiteralFromGroupExpressions.json"));

    private static Alias alias(org.apache.spark.sql.catalyst.expressions.Expression child, String name) {
        return new Alias(child, name, NamedExpression$.MODULE$.newExprId(), Catalyst.<String>seq(List.of()),
                Option.empty(), Catalyst.<String>seq(List.of()));
    }

    @Test
    void removesConstantGroupKeyAndProjectsItBack() {
        var base = Support.relation("S", 2, List.of(0, 1), new Random(7));
        var columns = Catalyst.<Attribute>list(base.output());
        var e = columns.get(0);
        var a = columns.get(1);
        var lit = Literal$.MODULE$.apply(2);
        var plan = new Aggregate(
                Catalyst.seq(List.of(e, lit)),
                Catalyst.seq(List.of(e, alias(lit, "c"),
                        alias(new Sum(a).toAggregateExpression(), "f"),
                        alias(new Max(a).toAggregateExpression(), "g"))),
                base,
                Option.empty());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var project = (Project) rewritten;
        var items = Catalyst.list(project.projectList());
        assertEquals(4, items.size());
        var below = project.child() instanceof Project nested ? nested.child() : project.child();
        assertTrue(below instanceof Aggregate);
        var inner = (Aggregate) below;
        assertEquals(1, Catalyst.list(inner.groupingExpressions()).size());
        assertEquals(e, Catalyst.list(inner.groupingExpressions()).get(0));
        var constantItems = project.child() instanceof Project nestedProject ? Catalyst.list(nestedProject.projectList()) : items;
        var constant = constantItems.get(1) instanceof Alias alias ? alias.child() : constantItems.get(1);
        assertEquals(lit, constant);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesColumnGroupKeyAlone() {
        var base = Support.relation("S", 2, List.of(0, 1), new Random(7));
        var columns = Catalyst.<Attribute>list(base.output());
        var e = columns.get(0);
        var a = columns.get(1);
        var plan = new Aggregate(
                Catalyst.seq(List.of(e, a)),
                Catalyst.seq(List.of(e, alias(a, "c"),
                        alias(new Sum(a).toAggregateExpression(), "f"),
                        alias(new Max(a).toAggregateExpression(), "g"))),
                base,
                Option.empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDistinctAggregateAlone() {
        var base = Support.relation("S", 2, List.of(0, 1), new Random(7));
        var columns = Catalyst.<Attribute>list(base.output());
        var e = columns.get(0);
        var a = columns.get(1);
        var lit = Literal$.MODULE$.apply(2);
        var plan = new Aggregate(
                Catalyst.seq(List.of(e, lit)),
                Catalyst.seq(List.of(e, alias(lit, "c"),
                        alias(new Sum(a).toAggregateExpression(true, Option.empty()), "f"),
                        alias(new Max(a).toAggregateExpression(), "g"))),
                base,
                Option.empty());
        assertEquals(plan, rule.apply(plan));
    }
}

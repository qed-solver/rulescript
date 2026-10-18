package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateProjectPullUpConstantsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateProjectPullUpConstants.json"));

    private static Alias alias(Expression child, String name) {
        return Alias$.MODULE$.apply(child, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static LogicalPlan aggregatedProject(Expression constant, Expression aggregated, Attribute x,
            LogicalPlan base) {
        var key = alias(constant, "k");
        var input = new Project(Catalyst.<NamedExpression>seq(List.of(key, x)), base);
        var k = key.toAttribute();
        var agg = alias(new Max(aggregated).toAggregateExpression(), "m");
        return new Aggregate(Catalyst.<Expression>seq(List.of(k, x)),
                Catalyst.<NamedExpression>seq(List.of(k, x, agg)), input, Option.empty());
    }

    private static LogicalPlan aggregatedProjectDistinct(Expression constant, Attribute x, LogicalPlan base) {
        var key = alias(constant, "k");
        var input = new Project(Catalyst.<NamedExpression>seq(List.of(key, x)), base);
        var k = key.toAttribute();
        var agg = alias(new Max(x).toAggregateExpression(true, Option.empty()), "m");
        return new Aggregate(Catalyst.<Expression>seq(List.of(k, x)),
                Catalyst.<NamedExpression>seq(List.of(k, x, agg)), input, Option.empty());
    }

    @Test
    void pullsConstantGroupKeyAboveAggregate() {
        var base = Support.relation("S", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregatedProject(Literal$.MODULE$.apply(true), x, x, base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        LogicalPlan node = rewritten;
        while (node instanceof Project) {
            node = node.children().head();
        }
        assertTrue(node instanceof Aggregate);
        var reduced = (Aggregate) node;
        assertEquals(List.of(x), Catalyst.list(reduced.groupingExpressions()));
        assertEquals(2, reduced.aggregateExpressions().size());
        assertTrue(reduced.child() instanceof Project);
        assertEquals(Catalyst.list(plan.output()).stream().map(Attribute::exprId).toList(),
                Catalyst.list(rewritten.output()).stream().map(Attribute::exprId).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNonConstantGroupKeyAlone() {
        var base = Support.relation("S", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregatedProject(Literal$.MODULE$.apply(2), x, x, base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDistinctAggregateAlone() {
        var base = Support.relation("S", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregatedProjectDistinct(Literal$.MODULE$.apply(true), x, base);
        assertEquals(plan, rule.apply(plan));
    }
}

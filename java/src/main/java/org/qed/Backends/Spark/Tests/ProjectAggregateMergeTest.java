package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.aggregate.Min;
import org.apache.spark.sql.catalyst.expressions.aggregate.Sum;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ProjectAggregateMergeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectAggregateMerge.json"));

    @Test
    void dropsUnusedAggregateCall() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var grouped = aggregate(base, List.<Expression>of(x), List.<NamedExpression>of(
                x,
                named(new Max(x), false, "f1"),
                named(new Min(x), false, "f2"),
                named(new Sum(x), false, "f3")));
        var out = Catalyst.<Attribute>list(grouped.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(out.get(0), out.get(1), out.get(3))), grouped);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var project = assertInstanceOf(Project.class, rewritten);
        assertEquals(3, Catalyst.list(project.projectList()).size());
        var reduced = assertInstanceOf(Aggregate.class, project.child());
        var items = Catalyst.list(reduced.aggregateExpressions());
        assertEquals(3, items.size());
        assertInstanceOf(Attribute.class, items.get(0));
        assertEquals(Max.class, function(items.get(1)).getClass());
        assertEquals(Sum.class, function(items.get(2)).getClass());
        assertEquals(Catalyst.list(reduced.output()).get(2).exprId(), Catalyst.list(project.projectList()).get(2).exprId());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesTwoGroupKeysAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var attrs = Catalyst.<Attribute>list(base.output());
        var x = attrs.get(0);
        var y = attrs.get(1);
        var grouped = aggregate(base, List.<Expression>of(x, y), List.<NamedExpression>of(
                x,
                y,
                named(new Max(x), false, "f1"),
                named(new Min(x), false, "f2"),
                named(new Sum(x), false, "f3")));
        var out = Catalyst.<Attribute>list(grouped.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(out.get(0), out.get(2), out.get(4))), grouped);

        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDistinctAggregateAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var grouped = aggregate(base, List.<Expression>of(x), List.<NamedExpression>of(
                x,
                named(new Max(x), true, "f1"),
                named(new Min(x), false, "f2"),
                named(new Sum(x), false, "f3")));
        var out = Catalyst.<Attribute>list(grouped.output());
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(out.get(0), out.get(1), out.get(3))), grouped);

        assertEquals(plan, rule.apply(plan));
    }

    private static Aggregate aggregate(LogicalPlan child, List<Expression> groups, List<NamedExpression> items) {
        return new Aggregate(Catalyst.seq(groups), Catalyst.seq(items), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static NamedExpression named(AggregateFunction function, boolean distinct, String name) {
        var expression = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, distinct,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return Alias$.MODULE$.apply((Expression) expression, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static AggregateFunction function(NamedExpression item) {
        return ((AggregateExpression) ((Alias) item).child()).aggregateFunction();
    }
}

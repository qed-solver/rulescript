package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PullOutVariantExtractionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PullOutVariantExtractions.json"));

    private static Expression extraction(Attribute variant) {
        return Add$.MODULE$.apply(variant, Literal$.MODULE$.apply(1), Add$.MODULE$.apply$default$3());
    }

    private static NamedExpression maxOf(Expression child, boolean distinct) {
        var aggregate = AggregateExpression$.MODULE$.apply(new Max(child), Complete$.MODULE$, distinct,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return Alias$.MODULE$.apply((Expression) aggregate, "m", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static LogicalPlan aggregate(List<Expression> groups, List<NamedExpression> items, LogicalPlan child) {
        return new Aggregate(Catalyst.seq(groups), Catalyst.seq(items), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void pullsVariantExtractionIntoProjectBelowAggregate() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var name = attrs.get(0);
        var variant = attrs.get(1);
        var item = maxOf(extraction(variant), false);
        var plan = aggregate(List.of(name), List.of(name, item), base);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var aggregate = assertInstanceOf(Aggregate.class, rewritten);
        var project = assertInstanceOf(Project.class, aggregate.child());
        assertSame(base, project.child());
        var projected = Catalyst.<NamedExpression>list(project.projectList());
        assertEquals(2, projected.size());
        assertEquals(name, projected.get(0));
        var hoisted = assertInstanceOf(Alias.class, projected.get(1));
        assertEquals(extraction(variant), hoisted.child());
        var items = Catalyst.<NamedExpression>list(aggregate.aggregateExpressions());
        assertEquals(2, items.size());
        var maxItem = assertInstanceOf(Alias.class, items.get(1));
        var function = assertInstanceOf(AggregateExpression.class, maxItem.child()).aggregateFunction();
        assertEquals(projected.get(1).toAttribute(), assertInstanceOf(Max.class, function).child());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesDistinctAggregateAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var name = attrs.get(0);
        var variant = attrs.get(1);
        var plan = aggregate(List.of(name), List.of(name, maxOf(extraction(variant), true)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesTwoGroupingKeysAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var name = attrs.get(0);
        var variant = attrs.get(1);
        var plan = aggregate(List.of(name, variant), List.of(name, variant, maxOf(extraction(variant), false)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAggregateOfMixedColumnsAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(7));
        var attrs = Catalyst.<Attribute>list(base.output());
        var name = attrs.get(0);
        var variant = attrs.get(1);
        var mixed = Add$.MODULE$.apply(name, variant, Add$.MODULE$.apply$default$3());
        var plan = aggregate(List.of(name), List.of(name, maxOf(mixed, false)), base);
        assertEquals(plan, rule.apply(plan));
    }
}

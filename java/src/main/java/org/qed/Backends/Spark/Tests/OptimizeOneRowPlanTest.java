package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
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

class OptimizeOneRowPlanTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("OptimizeOneRowPlan.json"));

    private static Aggregate groupBy(Attribute key, LogicalPlan child) {
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)), Catalyst.<NamedExpression>seq(List.of(key)),
                child, Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    private static Attribute keyOf(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output()).get(0);
    }

    @Test
    void groupByOnUniqueNonNullKeyBecomesProject() {
        var base = Support.relation("t", 1, List.of(0), new Random(1));
        var child = groupBy(keyOf(base), base);
        var plan = groupBy(keyOf(child), child);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var project = assertInstanceOf(Project.class, rewritten);
        assertEquals(child, project.child());
        assertEquals(List.of(keyOf(child)), Catalyst.<Attribute>list(project.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void groupByOverNonUniqueChildIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(2));
        var plan = groupBy(keyOf(base), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void groupByOnNullableKeyIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var child = groupBy(keyOf(base), base);
        var plan = groupBy(keyOf(child), child);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void groupByWithAggregateCallIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(0), new Random(4));
        var child = groupBy(keyOf(base), base);
        var key = keyOf(child);
        var max = AggregateExpression$.MODULE$.apply(new Max(key), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var maxItem = Alias$.MODULE$.apply((Expression) max, "m", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(key)),
                Catalyst.<NamedExpression>seq(List.of(key, maxItem)), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }
}

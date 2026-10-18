package org.qed.spark;

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
import org.apache.spark.sql.catalyst.expressions.aggregate.Count;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewriteDistinctAggregatesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RewriteDistinctAggregates.json"));

    @Test
    void rewritesCountDistinctOfConstantWithoutGroups() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = aggregateOf(List.of(), List.of(named(distinctCount(zero(), true), "cnt")), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate);
        var top = (Aggregate) rewritten;
        assertTrue(top.groupingExpressions().isEmpty());
        var topItems = Catalyst.list(top.aggregateExpressions());
        assertEquals(1, topItems.size());
        assertTrue(topItems.get(0) instanceof Alias topAlias && topAlias.child() instanceof AggregateExpression topCount
                && !topCount.isDistinct());
        assertTrue(top.child() instanceof Aggregate);
        var dedup = (Aggregate) top.child();
        assertEquals(2, Catalyst.list(dedup.groupingExpressions()).size());
        assertTrue(dedup.child() instanceof Project);
        var project = (Project) dedup.child();
        assertEquals(2, Catalyst.list(project.projectList()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesGroupedCountDistinctAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregateOf(List.<Expression>of(x), List.<NamedExpression>of(x, named(distinctCount(zero(), true), "cnt")), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonDistinctCountAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(3));
        var plan = aggregateOf(List.of(), List.of(named(distinctCount(zero(), false), "cnt")), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesCountDistinctOfColumnAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(4));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = aggregateOf(List.of(), List.of(named(distinctCount(x, true), "cnt")), base);
        assertEquals(plan, rule.apply(plan));
    }

    private static Expression zero() {
        return Literal$.MODULE$.apply(0L);
    }

    private static Expression distinctCount(Expression argument, boolean distinct) {
        return AggregateExpression$.MODULE$.apply(new Count(Catalyst.<Expression>seq(List.of(argument))),
                Complete$.MODULE$, distinct, scala.Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
    }

    private static NamedExpression named(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), scala.Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static LogicalPlan aggregateOf(List<Expression> groups, List<NamedExpression> items, LogicalPlan child) {
        return new Aggregate(Catalyst.<Expression>seq(groups), Catalyst.<NamedExpression>seq(items), child,
                scala.Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }
}

package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.aggregate.Sum;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
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

class PruneAggColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneAggCols.json"));

    private static Alias aliasOf(AggregateFunction function, String name, boolean distinct) {
        var expression = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, distinct,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return (Alias) Alias$.MODULE$.apply(expression, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Aggregate aggregate(Attribute key, LogicalPlan child, NamedExpression... items) {
        return new Aggregate(Catalyst.seq(List.<Expression>of(key)), Catalyst.seq(List.of(items)), child,
                Option.<AggregateHint>empty());
    }

    @Test
    void prunesUnusedAggregateUnderProject() {
        var base = Support.relation("s", 3, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var key = cols.get(0);
        var a = cols.get(1);
        var b = cols.get(2);
        var f = aliasOf(new Sum(a), "f", false);
        var g = aliasOf(new Max(b), "g", false);
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(key, f.toAttribute())),
                aggregate(key, base, key, f, g));

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var project = (Project) rewritten;
        assertTrue(project.child() instanceof Aggregate);
        var aggregate = (Aggregate) project.child();
        assertEquals(base, aggregate.child());
        var items = Catalyst.list(aggregate.aggregateExpressions());
        assertEquals(2, items.size());
        assertTrue(items.get(1) instanceof Alias alias && alias.name().equals("f"));
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsProjectThatSelectsThePrunedAggregate() {
        var base = Support.relation("s", 3, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var key = cols.get(0);
        var a = cols.get(1);
        var b = cols.get(2);
        var f = aliasOf(new Sum(a), "f", false);
        var g = aliasOf(new Max(b), "g", false);
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(key, g.toAttribute())),
                aggregate(key, base, key, f, g));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsProjectWhenPrunedAggregateIsDistinct() {
        var base = Support.relation("s", 3, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var key = cols.get(0);
        var a = cols.get(1);
        var b = cols.get(2);
        var f = aliasOf(new Sum(a), "f", false);
        var g = aliasOf(new Max(b), "g", true);
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(key, f.toAttribute())),
                aggregate(key, base, key, f, g));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsProjectWhenScanArityDiffers() {
        var base = Support.relation("s", 2, List.of(), new Random(7));
        var cols = Catalyst.<Attribute>list(base.output());
        var key = cols.get(0);
        var a = cols.get(1);
        var f = aliasOf(new Sum(a), "f", false);
        var g = aliasOf(new Max(a), "g", false);
        var plan = new Project(Catalyst.seq(List.<NamedExpression>of(key, f.toAttribute())),
                aggregate(key, base, key, f, g));
        assertEquals(plan, rule.apply(plan));
    }
}

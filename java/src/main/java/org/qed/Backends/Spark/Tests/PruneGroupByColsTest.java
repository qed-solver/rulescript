package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.aggregate.Min;
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

class PruneGroupByColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneGroupByCols.json"));

    private static NamedExpression aggregate(AggregateFunction function, boolean distinct, String name) {
        AggregateExpression call = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, distinct,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return Alias$.MODULE$.apply(call, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Aggregate groupedMaxMin(LogicalPlan input, Attribute key, Attribute a, Attribute b, boolean distinct) {
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)),
                Catalyst.<NamedExpression>seq(List.of(key, aggregate(new Max(a), distinct, "f"),
                        aggregate(new Min(b), false, "g"))),
                input, Option.<AggregateHint>empty());
    }

    @Test
    void prunesTheUnusedColumnBelowTheAggregate() {
        var base = Support.relation("s", 4, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var agg = groupedMaxMin(base, cols.get(0), cols.get(1), cols.get(2), false);
        var plan = new Project(Catalyst.<NamedExpression>seq(Catalyst.<Attribute>list(agg.output())), agg);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.child() instanceof Aggregate);
        var newAgg = (Aggregate) ((Project) rewritten).child();
        assertTrue(newAgg.child() instanceof Project inner && inner.child() == base);
        assertEquals(3, Catalyst.list(((Project) newAgg.child()).projectList()).size());
        assertEquals(1, Catalyst.list(newAgg.groupingExpressions()).size());
        var items = Catalyst.<NamedExpression>list(newAgg.aggregateExpressions());
        assertEquals(3, items.size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAnAggregateWithDistinctAlone() {
        var base = Support.relation("s", 4, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var agg = groupedMaxMin(base, cols.get(0), cols.get(1), cols.get(2), true);
        var plan = new Project(Catalyst.<NamedExpression>seq(Catalyst.<Attribute>list(agg.output())), agg);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAWiderInputAlone() {
        var base = Support.relation("s", 5, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var agg = groupedMaxMin(base, cols.get(0), cols.get(1), cols.get(2), false);
        var plan = new Project(Catalyst.<NamedExpression>seq(Catalyst.<Attribute>list(agg.output())), agg);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnOuterProjectThatDropsAnAggregateAlone() {
        var base = Support.relation("s", 4, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var agg = groupedMaxMin(base, cols.get(0), cols.get(1), cols.get(2), false);
        var out = Catalyst.<Attribute>list(agg.output());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(out.get(0), out.get(1))), agg);
        assertEquals(plan, rule.apply(plan));
    }
}
